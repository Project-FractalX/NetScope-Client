# NetScope Client

A Spring Boot library that turns **NetScope-Server gRPC endpoints into plain Java method calls**. Declare an annotated interface, add the dependency, configure a server — the library does the rest.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Authentication](#authentication)
  - [Named-server auth (YAML)](#named-server-auth-yaml)
  - [Inline auth (annotation)](#inline-auth-annotation)
- [Return Types](#return-types)
- [Overloaded Methods & Parameter Types](#overloaded-methods--parameter-types)
- [Remote Field Writes — @SetAttribute](#remote-field-writes--setattribute)
- [Server Introspection — GetDocs](#server-introspection--getdocs)
- [Imperative Template API](#imperative-template-api)
- [Advanced Streaming](#advanced-streaming)
  - [Batch streaming — invokeBatchStream](#batch-streaming--invokebatchstream)
  - [Stateful streaming — BidiStreamSession](#stateful-streaming--bidistreamsession)
- [Error Handling](#error-handling)
- [Reactive Support](#reactive-support)
- [How It Works](#how-it-works)

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21+ |
| Spring Boot | 3.2.x+ |
| NetScope-Server | 1.0.0 (on the remote side) |

---

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fractalx</groupId>
    <artifactId>netscope-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Reactive support** (`Mono` / `Flux` return types) is optional. Add `spring-boot-starter-webflux` if you need it — the library detects it automatically at runtime.

```xml
<!-- Optional: only needed for Mono / Flux return types -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

---

## Quick Start

### 1. Enable the client

Add `@EnableNetScopeClient` to your main application class. Point it at the package that contains your client interfaces.

```java
@SpringBootApplication
@EnableNetScopeClient(basePackages = "com.myapp.clients")
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 2. Configure servers

In `application.yml`, register each remote server under `netscope.client.servers`:

```yaml
netscope:
  client:
    servers:
      inventory-service:
        host: localhost
        port: 9090
```

### 3. Declare a client interface

Create an interface in the scanned package. Annotate it with `@NetScopeClient`, specifying:

- **`server`** — the key from `netscope.client.servers`
- **`beanName`** — the Spring bean name on the remote server

Method names must match the public methods exposed by that remote bean.

```java
package com.myapp.clients;

import org.fractalx.netscope.client.annotation.NetScopeClient;

@NetScopeClient(server = "inventory-service", beanName = "InventoryService")
public interface InventoryClient {
    int getStock(String productId);
    Product getProduct(String productId);
    List<Product> listProducts();
}
```

### 4. Inject and call

The library auto-registers the interface as a Spring bean. Inject it anywhere and call it like a local service.

```java
@Service
public class OrderService {

    @Autowired
    private InventoryClient inventory;

    public void placeOrder(String productId, int quantity) {
        int available = inventory.getStock(productId);
        if (available < quantity) {
            throw new IllegalStateException("Insufficient stock");
        }
        // ...
    }
}
```

---

## Configuration

All settings live under the `netscope.client` prefix.

```yaml
netscope:
  client:
    servers:
      # A named server entry — referenced by @NetScopeClient(server = "...")
      inventory-service:
        host: localhost       # default: localhost
        port: 9090            # default: 9090
        auth:
          type: NONE          # NONE | API_KEY | OAUTH  (default: NONE)

      auth-service:
        host: auth.internal
        port: 9091
        auth:
          type: OAUTH
          token-provider: myTokenProviderBean   # name of a Supplier<String> Spring bean

      billing-service:
        host: billing.internal
        port: 9092
        auth:
          type: API_KEY
          api-key: s3cr3t-k3y
```

### Inline server (no YAML entry required)

You can hardcode `host` and `port` directly on the annotation when you do not want a named config entry. Auth can also be specified inline — see [Inline auth](#inline-auth-annotation).

```java
// No auth (default)
@NetScopeClient(host = "localhost", port = 9090, beanName = "InventoryService")
public interface InventoryClient { ... }

// With inline API key
@NetScopeClient(host = "metrics.internal", port = 9090,
                authType = NetScopeClientConfig.AuthType.API_KEY, apiKey = "my-key",
                beanName = "MetricsService")
public interface MetricsClient { ... }
```

> When both `host`/`port` and `server` are provided, `host`/`port` take precedence.

---

## Authentication

### Named-server auth (YAML)

Configure auth under the server's `auth` block in `application.yml`.

#### No authentication (default)

```yaml
auth:
  type: NONE
```

#### API Key

```yaml
auth:
  type: API_KEY
  api-key: my-secret-key
```

The library attaches `x-api-key: <value>` to every gRPC request targeting that server.

#### OAuth / Bearer Token

```yaml
auth:
  type: OAUTH
  token-provider: myTokenProviderBean
```

`token-provider` must be the name of a Spring bean that implements `Supplier<String>`. The library calls it before each request and attaches `authorization: Bearer <token>`.

```java
@Component("myTokenProviderBean")
public class MyTokenProvider implements Supplier<String> {
    @Override
    public String get() {
        return fetchAccessToken();   // fetch / refresh a valid JWT here
    }
}
```

### Inline auth (annotation)

When using `host`/`port` directly on `@NetScopeClient`, configure auth with the annotation fields instead of YAML. This is useful for one-off or test clients that don't need a named server entry.

```java
// Inline API key
@NetScopeClient(
    host = "secure.internal", port = 9090,
    authType = NetScopeClientConfig.AuthType.API_KEY,
    apiKey   = "my-api-key",
    beanName = "SecureService"
)
public interface SecureClient {
    String getData();
}

// Inline OAuth (token fetched from a Supplier<String> Spring bean)
@NetScopeClient(
    host          = "auth.internal", port = 9090,
    authType      = NetScopeClientConfig.AuthType.OAUTH,
    tokenProvider = "myTokenProviderBean",
    beanName      = "AuthService"
)
public interface AuthClient {
    String getProfile();
}
```

> Two clients pointing at the same `host:port` but with different auth settings each get their own independent gRPC channel.

---

## Return Types

NetScope Client supports five return type categories on client interface methods:

| Return type | Behaviour |
|---|---|
| `T` (any type) | Blocking call; result deserialized to `T` |
| `void` | Blocking call; result discarded |
| `Mono<T>` | Non-blocking; gRPC call fires on subscription |
| `Flux<T>` | Streaming; each server response emitted as an element |
| `CompletableFuture<T>` | Async; wraps the blocking call via `supplyAsync` |

```java
@NetScopeClient(server = "inventory-service", beanName = "InventoryService")
public interface InventoryClient {

    // Blocking
    int getStock(String productId);

    // Async (no Reactor required)
    CompletableFuture<Integer> getStockAsync(String productId);

    // Reactive (requires spring-boot-starter-webflux)
    Mono<Integer> getStockMono(String productId);
    Flux<StockEvent> streamStockEvents(String productId);
}
```

---

## Overloaded Methods & Parameter Types

NetScope-Server supports **overloaded methods** — multiple methods sharing the same name but with different parameter types. The server resolves the right overload using the `parameter_types` field of each request.

The client **automatically populates `parameter_types`** from the Java method signature when using the declarative proxy interface. No extra configuration is needed.

```java
// Server exposes two overloads of "process":
//   String process(String id)
//   String process(int id)

@NetScopeClient(server = "order-service", beanName = "OrderService")
public interface OrderClient {
    String process(String id);   // sends parameter_types = ["String"]
    String process(int id);      // sends parameter_types = ["int"]
}
```

The proxy derives type names using `Class.getSimpleName()` — `"String"`, `"int"`, `"List"`, etc. — which matches the format the server's overload cache expects.

### Manual type hints with the template

When using `NetScopeTemplate` directly, use `withParameterTypes()` to specify type names explicitly:

```java
// Calls process(String) specifically
String result = netScope
    .server("order-service")
    .bean("OrderService")
    .withParameterTypes("String")
    .invoke("process", String.class, "order-42");

// Calls process(int) specifically
String result = netScope
    .server("order-service")
    .bean("OrderService")
    .withParameterTypes("int")
    .invoke("process", String.class, 42);
```

`withParameterTypes()` returns a new `BeanStep` — it does not mutate the original, so the same `BeanStep` can be reused safely.

---

## Remote Field Writes — @SetAttribute

NetScope-Server can expose Spring bean **fields** (not just methods) as network attributes. Reading a field works through a normal `invoke()` call. Writing a field uses the dedicated `SetAttribute` gRPC RPC, which also returns the **previous value** of the field.

### Declarative interface — `@SetAttribute`

Annotate a method with `@SetAttribute` to route it through the `SetAttribute` RPC:

```java
import org.fractalx.netscope.client.annotation.SetAttribute;

@NetScopeClient(server = "inventory-service", beanName = "InventoryService")
public interface InventoryClient {

    // Read the field via InvokeMethod (works as a regular method call)
    int getStockLevel();

    // Write the field via SetAttribute; annotation value = remote field name
    @SetAttribute("stockLevel")
    int updateStockLevel(int newLevel);   // returns previous value

    // When @SetAttribute has no value, the Java method name is used as the field name
    @SetAttribute
    String description(String newDescription);
}
```

Rules:
- The **first method parameter** is used as the new field value.
- The **return type** receives the field's previous value (use `void` to discard it).
- The remote field must be exposed with `@NetworkPublic` or `@NetworkSecured` on the server.
- Writing a `final` field is rejected by the server with `FAILED_PRECONDITION`.

### Template API — `setAttribute`

```java
// Untyped — returns Object (the previous value)
Object prev = netScope
    .server("inventory-service")
    .bean("InventoryService")
    .setAttribute("stockLevel", 500);

// Typed — previous value deserialized to Integer
Integer prev = netScope
    .server("inventory-service")
    .bean("InventoryService")
    .setAttribute("stockLevel", 500, Integer.class);
```

---

## Server Introspection — GetDocs

Call `getDocs()` on a `ServerStep` to retrieve live documentation for every method and field registered on the remote NetScope server:

```java
import org.fractalx.netscope.client.grpc.proto.DocsResponse;
import org.fractalx.netscope.client.grpc.proto.MethodInfo;

DocsResponse docs = netScope.server("inventory-service").getDocs();

for (MethodInfo info : docs.getMethodsList()) {
    System.out.printf(
        "[%s] %s.%s(%s) → %s  secured=%b%n",
        info.getKind(),
        info.getBeanName(),
        info.getMemberName(),
        info.getParametersList().stream()
            .map(p -> p.getType() + " " + p.getName())
            .collect(Collectors.joining(", ")),
        info.getReturnType(),
        info.getSecured()
    );
}
```

`DocsResponse` is the protobuf-generated type from `netscope.proto`. Each `MethodInfo` entry includes:

| Field | Description |
|---|---|
| `beanName` | Remote Spring bean name |
| `memberName` | Method or field name |
| `kind` | `METHOD` or `FIELD` |
| `parameters` | Name, type, index of each parameter |
| `returnType` | Simple class name of the return type |
| `secured` | Whether authentication is required |
| `writeable` | Whether the field can be written (fields only) |
| `isStatic` | Whether the member is static |
| `isFinal` | Whether the member is final |

---

## Imperative Template API

For dynamic or ad-hoc calls where you do not want to declare a typed interface, inject `NetScopeTemplate` directly:

```java
@Autowired
private NetScopeTemplate netScope;

// Typed call
int stock = netScope
    .server("inventory-service")
    .bean("InventoryService")
    .invoke("getStock", Integer.class, "SKU-001");

// Untyped call (returns Object)
Object result = netScope
    .server("inventory-service")
    .bean("InventoryService")
    .invoke("getStock", "SKU-001");

// Generic return type (e.g. List<Product>)
Type listType = new TypeToken<List<Product>>(){}.getType();
List<Product> products = (List<Product>) netScope
    .server("inventory-service")
    .bean("InventoryService")
    .invoke("listProducts", listType);

// With explicit parameter types for overload disambiguation
String result = netScope
    .server("order-service")
    .bean("OrderService")
    .withParameterTypes("String")
    .invoke("process", String.class, "order-42");

// Inline host/port with auth
NetScopeClientConfig.AuthConfig auth = new NetScopeClientConfig.AuthConfig();
auth.setType(NetScopeClientConfig.AuthType.API_KEY);
auth.setApiKey("my-key");

int val = netScope
    .server("localhost", 9090, auth)
    .bean("MetricsService")
    .invoke("getCpuUsage", Integer.class);

// Write a remote field
Integer prev = netScope
    .server("inventory-service")
    .bean("InventoryService")
    .setAttribute("stockLevel", 500, Integer.class);

// Introspect a server
DocsResponse docs = netScope.server("inventory-service").getDocs();
```

---

## Advanced Streaming

### Batch streaming — `invokeBatchStream`

Send multiple method invocations over **one persistent gRPC stream** connection and receive all responses as a `Flux<T>`. This avoids the per-call connection overhead for high-frequency scenarios.

```java
import org.fractalx.netscope.client.core.MethodCall;

@Autowired
private NetScopeTemplate netScope;

Flux<Integer> stocks = (Flux<Integer>) netScope
    .server("inventory-service")
    .bean("InventoryService")
    .invokeBatchStream(Integer.class,
        MethodCall.of("getStock", "SKU-001"),
        MethodCall.of("getStock", "SKU-002"),
        MethodCall.of("getStock", "SKU-003"));

stocks.subscribe(System.out::println);
```

A `MethodCall` can optionally override the default bean name for cross-bean invocations within the same stream:

```java
MethodCall.of("getStock", "SKU-001")               // uses BeanStep default bean
MethodCall.of("AnotherBean", "method", arg1, arg2) // overrides bean for this call
```

`invokeBatchStream` returns a cold `Flux<T>` — the gRPC stream opens on subscription.

### Stateful streaming — `BidiStreamSession`

For scenarios where you need to send an unbounded or dynamically-determined number of requests over a single long-lived connection, use `BidiStreamSession`:

```java
// 1. Open a session (gRPC stream opened immediately)
NetScopeTemplate.BidiStreamSession<String> session =
    netScope.server("inventory-service").openBidiStream("InventoryService", String.class);

// 2. Subscribe to the response Flux BEFORE sending any requests
(session.responseFlux() as Flux<String>)
    .subscribe(
        result  -> System.out.println("Response: " + result),
        error   -> log.error("Stream error", error),
        ()      -> System.out.println("Stream complete")
    );

// 3. Send requests one at a time (fluent chaining supported)
session.send("getStock", "SKU-001")
       .send("getStock", "SKU-002")
       .send("AnotherBean", "methodName", arg1);  // override bean per-call

// 4. Signal end of input
session.complete();
```

`openBidiStream` also accepts optional parameter type hints for exact overload resolution:

```java
// Every request in this session will send parameter_types = ["String"]
var session = netScope.server("order-service")
                      .openBidiStream("OrderService", String.class, "String");
```

> **Important:** Subscribe to `responseFlux()` before calling `send()`, otherwise early responses may be dropped.

---

## Error Handling

All exceptions thrown by the library are unchecked (`RuntimeException`).

| Exception | When thrown |
|---|---|
| `NetScopeRemoteException` | gRPC call reached the server but failed (wraps `StatusRuntimeException`) |
| `NetScopeClientException` | Local error before or after the call (serialization, config, missing token, etc.) |

`NetScopeRemoteException` extends `NetScopeClientException`, so you can catch both with a single handler.

```java
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.fractalx.netscope.client.exception.NetScopeRemoteException;

try {
    int stock = inventory.getStock("SKU-001");
} catch (NetScopeRemoteException e) {
    // gRPC-level failure — inspect the gRPC status code
    log.error("Remote call failed — gRPC status: {}", e.getStatus());
} catch (NetScopeClientException e) {
    // Local / configuration error
    log.error("Client error", e);
}
```

### Common gRPC status codes from the server

| Code | Meaning |
|---|---|
| `NOT_FOUND` | Member not found, or no overload matched the given parameter count |
| `INVALID_ARGUMENT` | Ambiguous overload — retry with explicit `parameter_types` |
| `UNAUTHENTICATED` | Missing or invalid credentials |
| `FAILED_PRECONDITION` | Attempted to write a `final` field, or called a field as a method |
| `INTERNAL` | Server-side invocation error |

---

## Reactive Support

`Mono` and `Flux` return types require Project Reactor on the classpath. Add `spring-boot-starter-webflux` to your dependencies — the library detects it automatically and never fails to start without it.

```java
@NetScopeClient(server = "events-service", beanName = "EventService")
public interface EventClient {

    // Single async result
    Mono<Event> getEvent(String eventId);

    // Streaming result — each server response is one element
    Flux<Event> streamAll();
}
```

```java
eventClient.streamAll()
    .filter(e -> e.getSeverity() == Severity.HIGH)
    .doOnNext(e -> log.warn("High-severity event: {}", e))
    .subscribe();
```

---

## How It Works

```
Your code
   │
   │  calls interface method
   ▼
JDK Dynamic Proxy  (NetScopeInvocationHandler)
   │  ├─ extracts parameter type names from reflection  → parameter_types
   │  ├─ checks @SetAttribute → routes to SetAttribute RPC if present
   │  └─ serializes args: Java → Jackson JSON → protobuf Value
   ▼
gRPC channel  (Netty, managed by NetScopeChannelFactory)
   │  auth interceptor attaches headers (x-api-key / authorization: Bearer)
   │
   │  InvokeMethod / SetAttribute / GetDocs / InvokeMethodStream RPC
   ▼
NetScope-Server
   │  resolves overload via exact parameter_types lookup or type inference
   │  invokes the target Spring bean method or reads/writes field
   │  serializes result → protobuf Value
   ▼
NetScopeValueConverter  (protobuf Value → Jackson → your return type)
   │
   ▼
Your code receives the deserialized result
```

- **Overload resolution**: `parameter_types` from method reflection are sent with every proxy call, enabling the server's exact overload lookup to fire instead of potentially-ambiguous type inference.
- **SetAttribute routing**: Methods annotated with `@SetAttribute` bypass `InvokeMethod` entirely and call the `SetAttribute` RPC, which returns the previous field value.
- **Auth-aware channel caching**: Channels are cached by `host:port:authType:credential`, so two clients pointing at the same address with different auth each get isolated channels.
- **Auto-configuration**: beans are registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. All beans are `@ConditionalOnMissingBean` so you can override any of them.
- **Serialization**: arguments and results travel as `google.protobuf.Value` (JSON-like). Jackson handles Java ↔ JSON; `JsonFormat` handles JSON ↔ protobuf.
- **Reactor isolation**: `ReactorOperations` is the only class that imports Reactor. It is only instantiated when Reactor is on the classpath, so the library never causes `ClassNotFoundException` without it.

---

## License

Apache License 2.0 — see [LICENSE](https://www.apache.org/licenses/LICENSE-2.0).

## Authors

- **Sathnindu Kottage** — [@sathninduk](https://github.com/sathninduk)
- **FractalX Team** — [https://github.com/project-FractalX](https://github.com/project-FractalX)
