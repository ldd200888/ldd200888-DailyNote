# Release APK 构建说明

项目已配置仅使用 `release` 签名配置来生成可商用包。

## 1) 配置签名

1. 复制示例文件：
   ```bash
   cp keystore.properties.example keystore.properties
   ```
2. 填写以下字段：
   - `STORE_FILE`：签名文件路径（相对项目根目录或绝对路径）
   - `STORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`

> 也可以不用 `keystore.properties`，改为使用同名环境变量。

## 2) 构建命令（不会编译 debug APK）

仅构建 Release APK：
```bash
./gradlew clean assembleRelease
```

## 3) 结果产物

- APK：`app/build/outputs/apk/release/app-release.apk`

## 4) 验证 APK 签名信息（可选）

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 5) 签名一致性

只要你持续使用同一把 keystore + alias，上述 release 包签名就会保持一致，可用于商用版本持续升级。

## 6) GitHub Actions 生成 Release APK（签名一致）

仓库已提供工作流：`.github/workflows/build-apk.yml`。

要在 CI 中保持签名一致，核心原则是：**始终使用同一个 keystore 和同一个 alias**。

在 GitHub 仓库 `Settings -> Secrets and variables -> Actions` 配置以下 Secrets：

- `RELEASE_KEYSTORE_BASE64`：将你的 keystore 文件做 base64 后的内容
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

本地可用如下命令生成 `RELEASE_KEYSTORE_BASE64`：

```bash
base64 -w 0 path/to/your-keystore.jks
```

配置后，`push main` 或手动触发 `workflow_dispatch` 会生成并上传：

- `daily-note-release-apk`

