# APKSignatureExtractor
获取应用和apk文件的Charstring，可用于XP模块签名助手（cn.xihan.sign），替代Libchecker中的一个小功能.
可直接导入AIDE中编译安装使用。

shell实现：
从单个apk
'''
unzip -p app.apk META-INF/CERT.RSA | openssl pkcs7 -inform DER -print_certs | openssl x509 -outform DER | xxd -p | tr -d '\n'
'''
从脚本文件同目录下所有apk
'''
#!/bin/bash
# 批量提取 APK 签名字符串的脚本
# 设置输出文件
OUTPUT_FILE="apk_charstrings.txt"
SEARCH_DIR="${1:-.}"
echo "=== APK 签名字符串批量提取工具 ==="
# 清空或创建输出文件
> "$OUTPUT_FILE"
# 查找目录中的所有 APK 文件
find "$SEARCH_DIR" -maxdepth 1 -name "*.apk" -type f | while read apk_file; do
    # 查找 APK 中可能的 RSA 证书文件
    rsa_files=$(unzip -l "$apk_file" 2>/dev/null | grep -E "META-INF/.*\.(RSA|DSA|EC)$" | awk '{print $4}')
    found_cert=0
    for rsa_file in $rsa_files; do
        echo "尝试使用签名文件: $rsa_file" | tee -a "$OUTPUT_FILE"
        # 提取并处理证书
        charstring=$(unzip -p "$apk_file" "$rsa_file" 2>/dev/null | openssl pkcs7 -inform DER -print_certs 2>/dev/null | openssl x509 -outform DER 2>/dev/null | xxd -p 2>/dev/null | tr -d '\n' 2>/dev/null)
        if [ -n "$charstring" ] && [ ${#charstring} -gt 100 ]; then
            echo "CharString:" | tee -a "$OUTPUT_FILE"
            echo "$charstring" | tee -a "$OUTPUT_FILE"
            echo "----------------------------------------" | tee -a "$OUTPUT_FILE"
            found_cert=1
            break
        fi
    done
done
'''

![1](https://github.com/user-attachments/assets/7d1c98f8-95e1-4e00-9539-e4be06d274fb)
![2](https://github.com/user-attachments/assets/bc1ca629-ac47-4d95-82a4-6f52ba659cbb)
![3](https://github.com/user-attachments/assets/372271c8-40b5-426f-81fd-0a277eac4cca)
![4](https://github.com/user-attachments/assets/a2e3530e-e05f-410a-8260-bdf7a7d99203)
