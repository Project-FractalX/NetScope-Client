# NetScope Client

A Spring Boot library that turns **NetScope-Server gRPC endpoints into plain Java method calls**. Declare an annotated interface, add the dependency, configure a server — the library does the rest.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Authentication](#authentication)
- [Return Types](#return-types)
- [Imperative Template API](#imperative-template-api)
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

You can hardcode `host` and `port` directly on the annotation when you do not want a named config entry:

```java
@NetScopeClient(host = "localhost", port = 9090, beanName = "InventoryService")
public interface InventoryClient { ... }
```

> When both `host`/`port` and `server` are provided, `host`/`port` take precedence. Inline connections do **not** attach auth headers.

---

## Authentication

### No authentication (default)

```yaml
auth:
  type: NONE
```

No headers are added to gRPC calls.

---

### API Key

```yaml
auth:
  type: API_KEY
  api-key: my-secret-key
```

The library attaches `x-api-key: <value>` to every gRPC request targeting that server.

---

### OAuth / Bearer Token

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
        // Fetch / refresh a valid JWT here
        return fetchAccessToken();
    }
}
```

---

## Return Types

NetScope Client supports four return type categories on client interface methods:

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
Type listType = new TypeToken<List<Product>>(){}.getType(); // Gson, or use TypeReference
List<Product> products = (List<Product>) netScope
    .server("inventory-service")
    .bean("InventoryService")
    .invoke("listProducts", listType);

// Inline host/port (bypasses named config)
int stock = netScope
    .server("localhost", 9090)
    .bean("InventoryService")
    .invoke("getStock", Integer.class, "SKU-001");
```

---

## Error Handling

All exceptions thrown by the library are unchecked (`RuntimeException`).

| Exception | When thrown |
|---|---|
| `NetScopeRemoteException` | gRPC call reached the server but failed (wraps `StatusRuntimeException`) |
| `NetScopeClientException` | Local error before or after the call (serialization, config, etc.) |

`NetScopeRemoteException` extends `NetScopeClientException`, so you can catch both with a single handler.

```java
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.fractalx.netscope.client.exception.NetScopeRemoteException;

try {
    int stock = inventory.getStock("SKU-001");
} catch (NetScopeRemoteException e) {
    // gRPC-level failure
    log.error("Remote call failed — gRPC status: {}", e.getStatus());
} catch (NetScopeClientException e) {
    // Local / configuration error
    log.error("Client error", e);
}
```

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
   │
   │  serializes args via Jackson → protobuf Value
   ▼
gRPC channel  (Netty, managed by NetScopeChannelFactory)
   │
   │  InvokeMethod / InvokeMethodStream RPC
   ▼
NetScope-Server
   │
   │  invokes the target Spring bean method
   │  serializes result → protobuf Value
   ▼
NetScopeValueConverter  (protobuf Value → Jackson → your return type)
   │
   ▼
Your code receives the deserialized result
```

- **Auto-configuration**: beans are registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. All beans are `@ConditionalOnMissingBean` so you can override any of them.
- **Serialization**: arguments and results travel as `google.protobuf.Value` (JSON-like). Jackson handles Java ↔ JSON; `JsonFormat` handles JSON ↔ protobuf.
- **Reactor isolation**: `ReactorOperations` is the only class that imports Reactor. It is only instantiated when Reactor is on the classpath, so the library never causes `ClassNotFoundException` without it.

---

## License

Apache License 2.0 — see [LICENSE](https://www.apache.org/licenses/LICENSE-2.0).

## Authors

- **Sathnindu Kottage** — [@sathninduk](https://github.com/sathninduk)
- **FractalX Team** — [https://github.com/project-FractalX](https://github.com/project-FractalX)
