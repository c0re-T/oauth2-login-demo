# oauth2-login-demo

Spring Security OAuth 2.0 Login 最小示例：用 GitHub 账号登录一个 Thymeleaf 服务端渲染的页面。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Spring Boot | 3.5.8 |
| Spring Security | 6.5.7 |
| Spring Framework | 6.2.14 |
| Thymeleaf | 3.1.3.RELEASE（含 `thymeleaf-extras-springsecurity6`） |
| JDK | 21 |

## 快速开始

### 1. 创建 GitHub OAuth App

GitHub → Settings → Developer settings → OAuth Apps → New OAuth App，填：

- **Homepage URL**：`http://localhost:8080/`
- **Callback URL / Redirect URI**：`http://localhost:8080/login/oauth2/code/github`
- **Grant User permissions in token scopes**：按需，本例只用到 `read:user`

Callback URL 必须和应用实际发出的 `redirect_uri` **逐字符完全一致**（协议、端口、路径都不能差），GitHub 在 authorize 端点就会做精确校验，不匹配直接报错。

建好后拿到 `Client ID` 和 `Client Secret`。

### 2. 配置本地密钥

`application.yaml` 里用占位符引用，真实值放在 **不被版本控制** 的 `application-local.yaml`：

```bash
cp src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
```

然后填入自己的 `client-id` / `client-secret`。

### 3. ⚠️ 如果你开着 GitHub 加速器（FastGithub / Watt Toolkit·Steam++ 等）

**这是本项目最容易踩的坑**：这类加速器靠 TLS 中间人加速 GitHub——它把 `github.com` 引到本地，再用自己的一根 CA 现签证书。这根根证书通常装进了 **Windows 系统证书库**，而 **JVM 不读系统证书库**，只认 JDK 自带的 `cacerts`。

结果就是"一半通一半断"：浏览器跳 GitHub 授权一切正常，但**你的 Java 进程拿 code 去换 token 时 TLS 握手失败**，Spring Security 统一跳 `/login?error` 并显示极具误导性的 `Invalid credentials`（看起来像密钥填错了，其实不是）。

验证是不是这个原因：

```bash
keytool -printcert -sslserver github.com:443
# 如果 Issuer 不是 GitHub 真实 CA，而是类似 CN=FastGithub / CN=SteamTools Certificate，就是被拦截了
```

解决办法二选一：

1. **保留加速器**，让 JVM 也信任系统证书库。IDEA：Run/Debug Configurations → 本应用 → Modify options → Add VM options：
   ```
   -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NULL
   ```
   命令行：`mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NULL"`
2. **退出加速器**后重试（前提是你的网络不挂加速器也能连上 GitHub）。

### 4. 启动

IDEA 里直接运行 `Oauth2LoginDemoApplication`，或命令行：

```bash
export JAVA_HOME="<你的 JDK 21 路径>"
mvn spring-boot:run
```

浏览器打开 <http://localhost:8080/> ，会自动跳到 GitHub 授权，回调后显示当前登录用户的信息。

## 登录流程：哪一步由谁完成

理解这个分工是排查这类问题的关键——**只有第 4 步是你的 Java 进程发起的 HTTPS 请求**，所以证书信任问题只会卡在第 4 步。

```
浏览器                         本应用(8080)                      GitHub
  |  GET /                        |                                |
  |------------------------------>| 未认证，oauth2Login 入口点      |
  |  302 /oauth2/authorization/github                              
  |<------------------------------|                                |
  |  GET /oauth2/authorization/github                              |
  |------------------------------>| 生成 state + PKCE，存进 session  |
  |  302 https://github.com/login/oauth/authorize?...              |
  |<------------------------------|                                |
  |==== ① 浏览器直连 GitHub，授权、登录、同意 =====>                |
  |  302 回 /login/oauth2/code/github?code=...&state=...           |
  |<---------------------------------------------------------------|
  |  GET 回调                       |                                |
  |------------------------------>| ② 校验 state（查 session）      |
  |                               |==== ③ ④ 服务端 HTTPS 换 token ==>|
  |                               |<==== 返回 access_token ==========|
  |                               |==== ⑤ 服务端 HTTPS 拉 /user ===>|
  |                               |<==== 用户信息 ===================|
  |  200 index.html                |                                |
  |<------------------------------| 认证写入 session，渲染页面       |
```

## 代码结构

```
src/main/java/com/ittxf/oauth2logindemo/
├── Oauth2LoginDemoApplication.java   启动类
└── controller/IndexController.java   受保护首页，注入 OAuth2User 与 OAuth2AuthorizedClient
src/main/resources/
├── application.yaml                  OAuth2 client registration（引用占位符）
├── application-local.yaml            真实密钥，已被 .gitignore 忽略
├── application-local.example.yaml    模板，纳入版本控制
└── templates/index.html              Thymeleaf 页面
```

**没有 `SecurityConfig` 类**：只要 classpath 上有 OAuth2 client 且存在 registration 配置，`oauth2Login()` 就会被自动启用，未认证请求直接 302 到 `/oauth2/authorization/github`。这一点由 `IndexPageRenderingTests.unauthenticatedRootRedirectsToGithubAuthorization` 守着。

## 测试

```bash
mvn test
```

`IndexPageRenderingTests` 用 `spring-security-test` 的 `oauth2Login()` / `oauth2Client()` 伪造已认证主体，**完全不依赖 GitHub 网络**即可验证渲染：

- `unauthenticatedRootRedirectsToGithubAuthorization` —— 入口点跳转正确
- `rendersLoggedInUsernameInBody` —— 断言的是**渲染后的 HTML**，而不是 model 属性。模板变量名写错（如 `${userName}` vs `username`）在 model 层是看不出来的，必须断言 HTML。

## 常见问题排查

`/login?error` 上的 "Invalid credentials" 是 Spring Security 对**所有** OAuth2 登录失败的统一文案，零信息量，不要据此判断原因。按层拆开验证：

| 想确认的事 | 不依赖 GitHub 的办法 |
| --- | --- |
| 应用发出的 authorize URL 是否正确（占位符没解析、profile 没激活等） | `curl -i http://localhost:8080/oauth2/authorization/github`，看 `Location` 里的 `client_id` / `scope` / `redirect_uri` / `code_challenge` |
| `client_secret` 到底对不对 | 拿**假 code** 敲 token 端点：返回 `bad_verification_code` = 凭证正确；返回 `incorrect_client_credentials` = 密钥错 |
| 登录成功后的页面渲染是否正常 | 跑 `IndexPageRenderingTests`，不碰网络 |
| **JVM 能不能建立到 GitHub 的 TLS** | `keytool -printcert -sslserver github.com:443` 看签发者；或写个 `HttpClient` 小探针，对比 `curl` 的结果 |

每次都从 `http://localhost:8080/` **全新发起**，别复用旧的授权页：授权请求的 `state` 存在内存 session 里，应用一重启就失效，旧页面上的回调必然以 `authorization_request_not_found` 失败。
