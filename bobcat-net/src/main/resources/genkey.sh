# gen jks
keytool -genkeypair -alias mykey -keyalg RSA -keysize 2048 -keystore mykeystore.jks -dname "CN=localhost, OU=localhost, O=localhost, L=SH, ST=SH, C=CN" -keypass "123456" -storepass "123456" -validity 3600
keytool -exportcert -alias mykey -file mycert.cer -keystore mykeystore.jks
keytool -importcert -alias hello -file mycert.cer -keystore mykeystore.jks