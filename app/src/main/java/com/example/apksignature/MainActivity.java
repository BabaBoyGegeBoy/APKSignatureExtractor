package com.example.apksignature;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import java.io.File;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {

    private ListView listView;
    private Button btnInstalledApps, btnSystemApps, btnSelectApk;
    private List<AppInfo> appList;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 检查存储权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1002);
            }
        }

        initViews();
        setupClickListeners();

        appList = new ArrayList<AppInfo>();
        List<String> initialList = new ArrayList<String>();
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, initialList);
        listView.setAdapter(adapter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1002) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showShortToast("存储权限已授予");
            } else {
                showShortToast("需要存储权限来读取APK文件");
            }
        }
    }

    private void initViews() {
        listView = (ListView) findViewById(R.id.listView);
        btnInstalledApps = (Button) findViewById(R.id.btn_installed_apps);
        btnSystemApps = (Button) findViewById(R.id.btn_system_apps);
        btnSelectApk = (Button) findViewById(R.id.btn_select_apk);
    }

    private void setupClickListeners() {
        btnInstalledApps.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showUserApps();
				}
			});

        btnSystemApps.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showSystemApps();
				}
			});

        btnSelectApk.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectApkFile();
				}
			});

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					if (position < appList.size()) {
						AppInfo appInfo = appList.get(position);
						extractAndShowSignature(appInfo);
					}
				}
			});
    }

    // 显示用户应用
    private void showUserApps() {
        loadApps(false);
    }

    // 显示系统应用
    private void showSystemApps() {
        loadApps(true);
    }

    // 统一的加载应用方法
    private void loadApps(boolean showSystem) {
        appList.clear();
        List<String> displayList = new ArrayList<String>();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        // 创建临时列表来存储应用信息用于排序
        final List<AppInfo> tempAppList = new ArrayList<AppInfo>();

        for (ApplicationInfo packageInfo : packages) {
            boolean isSystemApp = (packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            // 根据参数决定显示哪种应用
            if ((showSystem && isSystemApp) || (!showSystem && !isSystemApp)) {
                String appName = pm.getApplicationLabel(packageInfo).toString();
                AppInfo appInfo = new AppInfo(packageInfo.packageName, appName);
                appInfo.isSystemApp = isSystemApp;
                tempAppList.add(appInfo);
            }
        }

        // 排序逻辑
        final java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CHINA);

        java.util.Collections.sort(tempAppList, new java.util.Comparator<AppInfo>() {
				@Override
				public int compare(AppInfo app1, AppInfo app2) {
					return collator.compare(app1.appName, app2.appName);
				}
			});

        // 将排序后的应用添加到正式列表
        appList.addAll(tempAppList);

        // 创建显示列表，系统应用用不同的图标
        for (AppInfo appInfo : appList) {
            String icon = (appInfo.isSystemApp) ? "⚙️" : "📱";
            displayList.add(icon + " " + appInfo.appName + "\n📦 " + appInfo.packageName);
        }

        adapter.clear();
        for (String item : displayList) {
            adapter.add(item);
        }
        adapter.notifyDataSetChanged();

        String type = showSystem ? "系统" : "用户";
        showShortToast("已加载 " + appList.size() + " 个" + type + "应用");
    }

    private void selectApkFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        startActivityForResult(Intent.createChooser(intent, "选择APK文件"), 1001);
    }

    // 统一的签名提取和显示方法
    private void extractAndShowSignature(AppInfo appInfo) {
        String charString = extractSignature(appInfo);

        if (charString != null && charString.length() > 0) {
            CertificateInfo certInfo = parseCertificateInfo(charString);
            showSignatureDialog(appInfo, charString, certInfo);
        } else {
            showShortToast("签名提取失败");
        }
    }

    // 统一的签名提取方法
    private String extractSignature(AppInfo appInfo) {
        if (appInfo.isInstalledApp) {
            return extractInstalledAppSignature(appInfo.packageName);
        } else {
            return extractApkFileSignature(appInfo.filePath);
        }
    }

    // 提取已安装应用签名
    private String extractInstalledAppSignature(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);

            if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                android.content.pm.Signature signature = packageInfo.signatures[0];
                byte[] certBytes = signature.toByteArray();
                String charString = bytesToHex(certBytes);
                showShortToast("签名长度: " + charString.length());
                return charString;
            }
        } catch (Exception e) {
            e.printStackTrace();
            showShortToast("错误: " + e.getMessage());
        }
        return null;
    }

    // 提取APK文件签名
    private String extractApkFileSignature(String apkFilePath) {
        try {
            File apkFile = new File(apkFilePath);
            if (!apkFile.exists()) {
                return null;
            }

            List<String> certEntries = findSignatureEntries(apkFile);
            if (certEntries.isEmpty()) {
                showShortToast("未找到签名文件");
                return null;
            }

            for (String entry : certEntries) {
                byte[] certData = extractEntryFromZip(apkFile, entry);
                if (certData != null && certData.length > 0) {
                    String charString = bytesToHex(certData);
                    showShortToast("找到签名: " + entry + ", 长度: " + charString.length());

                    if (charString.length() > 100) {
                        return charString;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showShortToast("提取异常: " + e.getMessage());
        }
        return null;
    }

    // 十六进制字符串转字节数组
    private byte[] hexToBytes(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0) {
            return null;
        }

        byte[] data = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }

    // 字节数组转十六进制字符串
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // 解析证书信息
    private CertificateInfo parseCertificateInfo(String charString) {
        CertificateInfo info = new CertificateInfo();

        try {
            byte[] certData = hexToBytes(charString);
            if (certData == null) return info;

            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) 
                certFactory.generateCertificate(new java.io.ByteArrayInputStream(certData));

            // 基础证书信息
            info.serialNumber = certificate.getSerialNumber().toString();
            info.issuer = formatDN(certificate.getIssuerX500Principal().getName());
            info.subject = formatDN(certificate.getSubjectX500Principal().getName());
            info.validFrom = formatDate(certificate.getNotBefore());
            info.validTo = formatDate(certificate.getNotAfter());
            info.signatureAlgorithm = certificate.getSigAlgName();
            info.publicKeyAlgorithm = certificate.getPublicKey().getAlgorithm();

            // 版本信息
            info.version = "v" + certificate.getVersion();

            // 密钥大小
            try {
                if (certificate.getPublicKey() instanceof java.security.interfaces.RSAPublicKey) {
                    java.security.interfaces.RSAPublicKey rsaKey = (java.security.interfaces.RSAPublicKey) certificate.getPublicKey();
                    info.keySize = rsaKey.getModulus().bitLength() + " 位";
                }
            } catch (Exception e) {
                info.keySize = "未知";
            }

            // 计算哈希值
            java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
            info.md5 = bytesToHex(md5.digest(certData));

            java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
            info.sha1 = bytesToHex(sha1.digest(certData));

            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            info.sha256 = bytesToHex(sha256.digest(certData));

            info.parsedSuccessfully = true;

        } catch (Exception e) {
            e.printStackTrace();
            // 即使解析失败，也计算哈希值
            try {
                byte[] certData = hexToBytes(charString);
                if (certData != null) {
                    java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
                    info.md5 = bytesToHex(md5.digest(certData));

                    java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
                    info.sha1 = bytesToHex(sha1.digest(certData));

                    java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
                    info.sha256 = bytesToHex(sha256.digest(certData));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return info;
    }

    // 格式化专有名称（DN）
    private String formatDN(String dn) {
        if (dn == null) return null;
        return dn.replace(", ", ",\n");
    }

    // 格式化日期
    private String formatDate(java.util.Date date) {
        if (date == null) return null;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
        return sdf.format(date);
    }

    // 统一的对话框显示方法
    private void showSignatureDialog(final AppInfo appInfo, final String charString, final CertificateInfo certInfo) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("📄 签名详情 - " + appInfo.appName);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        // 应用基本信息
        addSectionTitle(layout, "📱 应用信息");
        addCopyableItem(layout, "应用名称", appInfo.appName, "应用名称");
        addCopyableItem(layout, "包名", appInfo.packageName, "包名");

        if (certInfo.parsedSuccessfully) {
            // 证书基本信息
            addSectionTitle(layout, "📋 证书信息");
            addCopyableItem(layout, "版本", certInfo.version, "证书版本");
            addCopyableItem(layout, "序列号", certInfo.serialNumber, "序列号");

            if (certInfo.keySize != null) {
                addCopyableItem(layout, "密钥大小", certInfo.keySize, "密钥大小");
            }

            // 签发信息
            addSectionTitle(layout, "🏢 签发信息");
            addCopyableItem(layout, "签发者", certInfo.issuer, "签发者");
            addCopyableItem(layout, "主题", certInfo.subject, "主题");

            // 有效期
            addSectionTitle(layout, "📅 有效期");
            addCopyableItem(layout, "生效时间", certInfo.validFrom, "生效时间");
            addCopyableItem(layout, "过期时间", certInfo.validTo, "过期时间");

            // 算法信息
            addSectionTitle(layout, "🔐 算法信息");
            addCopyableItem(layout, "签名算法", certInfo.signatureAlgorithm, "签名算法");
            addCopyableItem(layout, "公钥算法", certInfo.publicKeyAlgorithm, "公钥算法");
        }

        // 哈希值
        addSectionTitle(layout, "🔍 指纹信息");
        if (certInfo.md5 != null) {
            addCopyableItem(layout, "MD5", formatHash(certInfo.md5), "MD5");
        }
        if (certInfo.sha1 != null) {
            addCopyableItem(layout, "SHA-1", formatHash(certInfo.sha1), "SHA-1");
        }
        if (certInfo.sha256 != null) {
            addCopyableItem(layout, "SHA-256", formatHash(certInfo.sha256), "SHA-256");
        }

        // CharString
        addSectionTitle(layout, "📜 原始数据");
        String preview = charString.length() > 150 ? 
            charString.substring(0, 150) + "..." : charString;
        addCopyableItem(layout, "CharString (" + charString.length() + " 字符)", preview, "CharString");

        scrollView.addView(layout);
        builder.setView(scrollView);

        // 添加操作按钮
        builder.setPositiveButton("复制全部", new android.content.DialogInterface.OnClickListener() {
				@Override
				public void onClick(android.content.DialogInterface dialog, int which) {
					copyAllSignatureInfo(appInfo, charString, certInfo);
				}
			});

        builder.setNeutralButton("复制CharString", new android.content.DialogInterface.OnClickListener() {
				@Override
				public void onClick(android.content.DialogInterface dialog, int which) {
					copyToClipboard(charString, "CharString已复制");
				}
			});

        builder.setNegativeButton("关闭", null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // 设置对话框大小
        android.view.WindowManager.LayoutParams layoutParams = new android.view.WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
    }

    // 添加分区标题
    private void addSectionTitle(android.widget.LinearLayout layout, String title) {
        android.widget.TextView titleView = new android.widget.TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(0xFF2196F3);
        titleView.setPadding(0, 20, 0, 10);
        layout.addView(titleView);

        // 添加分隔线
        android.view.View divider = new android.view.View(this);
        android.widget.LinearLayout.LayoutParams dividerParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dividerParams.setMargins(0, 5, 0, 15);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(0xFFE0E0E0);
        layout.addView(divider);
    }

    // 添加可复制的信息项
    private void addCopyableItem(android.widget.LinearLayout layout, String title, String content, final String copyLabel) {
        if (content == null) return;

        // 创建final副本
        final String finalContent = content;
        final String finalCopyLabel = copyLabel;

        android.widget.LinearLayout itemLayout = new android.widget.LinearLayout(this);
        itemLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        itemLayout.setPadding(0, 10, 0, 10);

        // 标题
        android.widget.TextView titleView = new android.widget.TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(0xFF2196F3);
        itemLayout.addView(titleView);

        // 内容（可点击复制）
        android.widget.TextView contentView = new android.widget.TextView(this);
        contentView.setText(finalContent);
        contentView.setTextSize(12);
        contentView.setPadding(20, 5, 20, 5);
        contentView.setBackgroundColor(0xFFF5F5F5);
        contentView.setClickable(true);
        contentView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					copyToClipboard(finalContent, finalCopyLabel + "已复制");
				}
			});
        itemLayout.addView(contentView);

        layout.addView(itemLayout);
    }

    // 格式化哈希值（添加冒号分隔）
    private String formatHash(String hash) {
        if (hash == null) return null;
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < hash.length(); i += 2) {
            if (i > 0) formatted.append(":");
            if (i + 2 <= hash.length()) {
                formatted.append(hash.substring(i, i + 2).toUpperCase());
            }
        }
        return formatted.toString();
    }

    // 复制到剪贴板
    private void copyToClipboard(String text, String toastMessage) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("APK签名信息", text);
        clipboard.setPrimaryClip(clip);
        showShortToast(toastMessage);
    }

    // 复制全部签名信息
    private void copyAllSignatureInfo(AppInfo appInfo, String charString, CertificateInfo certInfo) {
        StringBuilder allInfo = new StringBuilder();

        allInfo.append("=== 应用签名信息 ===\n\n");

        // 应用信息
        allInfo.append("【应用信息】\n");
        allInfo.append("应用名称: ").append(appInfo.appName).append("\n");
        allInfo.append("包名: ").append(appInfo.packageName).append("\n\n");

        if (certInfo.parsedSuccessfully) {
            // 证书信息
            allInfo.append("【证书信息】\n");
            allInfo.append("版本: ").append(certInfo.version).append("\n");
            allInfo.append("序列号: ").append(certInfo.serialNumber).append("\n");
            if (certInfo.keySize != null) {
                allInfo.append("密钥大小: ").append(certInfo.keySize).append("\n");
            }
            allInfo.append("\n");

            // 签发信息
            allInfo.append("【签发信息】\n");
            allInfo.append("签发者: ").append(certInfo.issuer.replace("\n", " ")).append("\n");
            allInfo.append("主题: ").append(certInfo.subject.replace("\n", " ")).append("\n");
            allInfo.append("\n");

            // 有效期
            allInfo.append("【有效期】\n");
            allInfo.append("生效时间: ").append(certInfo.validFrom).append("\n");
            allInfo.append("过期时间: ").append(certInfo.validTo).append("\n");
            allInfo.append("\n");

            // 算法信息
            allInfo.append("【算法信息】\n");
            allInfo.append("签名算法: ").append(certInfo.signatureAlgorithm).append("\n");
            allInfo.append("公钥算法: ").append(certInfo.publicKeyAlgorithm).append("\n");
            allInfo.append("\n");
        }

        // 指纹信息
        allInfo.append("【指纹信息】\n");
        if (certInfo.md5 != null) {
            allInfo.append("MD5: ").append(formatHash(certInfo.md5)).append("\n");
        }
        if (certInfo.sha1 != null) {
            allInfo.append("SHA-1: ").append(formatHash(certInfo.sha1)).append("\n");
        }
        if (certInfo.sha256 != null) {
            allInfo.append("SHA-256: ").append(formatHash(certInfo.sha256)).append("\n");
        }
        allInfo.append("\n");

        // CharString
        allInfo.append("【CharString】\n");
        allInfo.append("长度: ").append(charString.length()).append(" 字符\n");
        allInfo.append("内容: ").append(charString).append("\n");

        copyToClipboard(allInfo.toString(), "全部签名信息已复制");
    }

    // 查找签名文件
    private List<String> findSignatureEntries(File apkFile) {
        List<String> entries = new ArrayList<String>();
        try {
            ZipFile zipFile = new ZipFile(apkFile);
            java.util.Enumeration<? extends ZipEntry> entriesEnum = zipFile.entries();

            while (entriesEnum.hasMoreElements()) {
                ZipEntry entry = entriesEnum.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    entries.add(name);
                }
            }
            zipFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return entries;
    }

    // 从ZIP文件中提取条目
    private byte[] extractEntryFromZip(File zipFile, String entryName) {
        java.io.InputStream is = null;
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipFile);
            ZipEntry entry = zf.getEntry(entryName);
            if (entry == null) {
                // 尝试使用另一种方式查找entry
                java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.getName().equals(entryName)) {
                        entry = e;
                        break;
                    }
                }
            }

            if (entry != null) {
                is = zf.getInputStream(entry);
                int size = (int) entry.getSize();
                if (size <= 0) {
                    // 如果无法获取大小，使用缓冲读取
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] data = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, bytesRead);
                    }
                    return buffer.toByteArray();
                } else {
                    byte[] buffer = new byte[size];
                    int bytesRead = is.read(buffer);
                    if (bytesRead == size) {
                        return buffer;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) is.close();
                if (zf != null) zf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    // 简短的Toast工具方法
    private void showShortToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            String filePath = getPathFromUri(uri);

            if (filePath != null && filePath.endsWith(".apk")) {
                // 获取原始文件名
                String originalFileName = getOriginalFileName(uri);
                AppInfo appInfo = new AppInfo(filePath);

                // 如果获取到原始文件名，就使用它
                if (originalFileName != null && !originalFileName.isEmpty()) {
                    appInfo.appName = originalFileName;
                }

                appList.add(appInfo);

                // 在列表中显示原始文件名
                adapter.add("📄 " + appInfo.appName);
                adapter.notifyDataSetChanged();

                // 直接开始提取
                extractAndShowSignature(appInfo);
            } else {
                showShortToast("请选择有效的APK文件");
            }
        }
    }

    // 获取原始文件名的方法
    private String getOriginalFileName(Uri uri) {
        if (uri == null) return null;

        String fileName = null;

        // 方法1：从URI路径中提取文件名
        String uriString = uri.toString();
        if (uriString != null) {
            int cut = uriString.lastIndexOf('/');
            if (cut != -1) {
                fileName = uriString.substring(cut + 1);
                // 解码URL编码的文件名
                try {
                    fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 方法2：通过ContentResolver查询
        if (fileName == null || fileName.isEmpty()) {
            try {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    // 尝试不同的列名来获取文件名
                    String[] columnNames = {
                        "_display_name",
                        "display_name",
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME
                    };

                    for (String column : columnNames) {
                        int nameIndex = cursor.getColumnIndex(column);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                            if (fileName != null && !fileName.isEmpty()) {
                                break;
                            }
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 方法3：从文件路径中提取
        if (fileName == null || fileName.isEmpty()) {
            String filePath = getPathFromUri(uri);
            if (filePath != null) {
                File file = new File(filePath);
                fileName = file.getName();
            }
        }

        return fileName;
    }

    private String getPathFromUri(Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file")) {
            return uri.getPath();
        }

        // 对于 content:// URI，使用更可靠的方法
        if (scheme.equals("content")) {
            try {
                // 方法1：通过ContentResolver查询
                String[] projection = { android.provider.MediaStore.MediaColumns.DATA };
                android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA);
                    String path = cursor.getString(columnIndex);
                    cursor.close();
                    if (path != null) {
                        return path;
                    }
                }
                if (cursor != null) {
                    cursor.close();
                }

                // 方法2：尝试直接复制文件到缓存目录
                return copyFileToCache(uri);

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        return null;
    }

    // 将URI指向的文件复制到缓存目录
    private String copyFileToCache(Uri uri) {
        try {
            // 创建缓存文件
            File cacheDir = getCacheDir();
            File tempFile = new File(cacheDir, "temp_apk_" + System.currentTimeMillis() + ".apk");

            // 复制文件
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            return tempFile.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // AppInfo类
    class AppInfo {
        String packageName;
        String appName;
        String filePath;
        boolean isInstalledApp;
        boolean isSystemApp;

        AppInfo(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
            this.isInstalledApp = true;
            this.isSystemApp = false;
        }

        AppInfo(String filePath) {
            this.filePath = filePath;
            this.appName = new File(filePath).getName();
            this.isInstalledApp = false;
            this.isSystemApp = false;
        }
    }

    // CertificateInfo类
    class CertificateInfo {
        String serialNumber;
        String issuer;
        String subject;
        String validFrom;
        String validTo;
        String signatureAlgorithm;
        String publicKeyAlgorithm;
        String version;
        String keySize;
        String md5;
        String sha1;
        String sha256;
        boolean parsedSuccessfully = false;
    }
}
