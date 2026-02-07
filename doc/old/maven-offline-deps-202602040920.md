# Maven 离线依赖包方案（java-agent）

## 1. 目标与范围

- 目标：把 `F:\ai-code\java-agent` 项目在外网完整解析并下载所有依赖，打包为离线仓库，便于同步到内网使用。
- 范围：`pom.xml` 中的所有项目依赖、插件依赖、父 `pom`、扩展、构建期需要的依赖。

## 2. 外网机器下载离线依赖

### 2.1 推荐方式：独立本地仓库目录

> 目的：避免污染全局 `~/.m2`，同时便于打包迁移。

**步骤 1：准备独立仓库目录**

```bash
mkdir -p F:\ai-code\java-agent\.m2-offline
```

**步骤 2：在外网完整拉取依赖**

> 需要在外网机器执行。若项目有 profile，请在命令里追加 `-P`。

```bash
cd F:\ai-code\java-agent
mvn -U -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline clean package
mvn -U -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline dependency:go-offline
```

说明：
- `clean package` 能触发插件依赖下载。
- `dependency:go-offline` 补齐项目依赖与插件依赖。

**步骤 3：离线验证**

```bash
mvn -o -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline clean package
```

若该命令不再尝试联网，说明离线依赖齐全。

**步骤 4：打包离线仓库**

```bash
cd F:\ai-code\java-agent
powershell Compress-Archive -Path .m2-offline\* -DestinationPath m2-offline.zip -Force
```

### 2.2 备用方式：拷贝全量 `~/.m2/repository`

> 不推荐，但简单粗暴。若使用该方式，直接打包 `C:\Users\<用户名>\.m2\repository`。

## 3. 内网加载离线依赖

### 3.1 方式 A：解压并指定本地仓库

```bash
mkdir -p F:\maven-repo
powershell Expand-Archive -Path F:\ai-code\java-agent\m2-offline.zip -DestinationPath F:\maven-repo -Force
```

然后在构建命令中指定本地仓库：

```bash
cd F:\ai-code\java-agent
mvn -o -DskipTests -Dmaven.repo.local=F:\maven-repo clean package
```

### 3.2 方式 B：在 `settings.xml` 固定本地仓库

在内网机器创建 `~/.m2/settings.xml`：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <localRepository>F:/maven-repo</localRepository>
</settings>
```

之后直接执行：

```bash
mvn -o -DskipTests clean package
```

## 4. 上传到内网 Maven 仓库（可选）

> 适用于将离线依赖统一托管在内网 `Nexus` 或 `Artifactory`。

### 4.1 推荐方案：部署内网私服并批量上传

1. 在内网部署仓库管理器，创建 `hosted` 类型的 `maven` 仓库。
2. 在外网机器将离线仓库传入内网。
3. 使用仓库管理器的 “批量导入” 功能导入离线仓库（不同产品入口不同）。

### 4.2 备用方案：使用 `mvn deploy` 上传

> 需要在 `pom.xml` 或 `settings.xml` 中配置 `distributionManagement` 和 `server` 账号。

示例命令（占位写法）：

```bash
mvn -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline \
    -DaltDeploymentRepository=internal::default::http://<内网仓库地址>/repository/maven-releases \
    deploy
```

说明：
- 如果项目依赖包含 `-SNAPSHOT`，需要另建 `snapshots` 仓库并替换地址。
- 若无统一发布流程，可使用 `deploy:deploy-file` 单个上传，但维护成本更高。

## 5. 常见问题与检查清单

- 如果出现缺包，优先检查是否遗漏了必要的 `profile`。
- 某些插件依赖仅在特定 `goal` 触发时下载，可增加一次 `mvn -DskipTests verify`。
- 若项目存在 `system` scope 或 `file` 依赖，需要单独搬运对应文件。
- 多模块项目建议在根目录执行命令，确保子模块依赖一并下载。
- 内网构建必须使用 `-o` 或断网验证，确保不会访问公网。

## 6. 建议的操作清单（可直接复制）

### 6.1 外网机器

```bash
cd F:\ai-code\java-agent
mvn -U -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline clean package
mvn -U -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline dependency:go-offline
mvn -o -DskipTests -Dmaven.repo.local=F:\ai-code\java-agent\.m2-offline clean package
powershell Compress-Archive -Path .m2-offline\* -DestinationPath m2-offline.zip -Force
```

### 6.2 内网机器

```bash
mkdir -p F:\maven-repo
powershell Expand-Archive -Path F:\ai-code\java-agent\m2-offline.zip -DestinationPath F:\maven-repo -Force
cd F:\ai-code\java-agent
mvn -o -DskipTests -Dmaven.repo.local=F:\maven-repo clean package
```