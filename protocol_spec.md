# Math Server Protocol Specification v1.0

**Course:** CE/CS 4390 / 5390 — Computer Networks — Spring 2026  
**Team:** Gaurang & Vedant  
**Date:** March 31, 2026  

---

## Transport & Encoding

- All communication uses **TCP** sockets.
- Messages are **UTF-8** strings terminated by a newline (`\n`).
- Fields are separated by the pipe character (`|`).
- Maximum message length: **1024 bytes**.
- Default server port: **6789**.

---

## Message Types

### 1. JOIN (Client → Server)

Registers the client with the server.

**Format:** `JOIN|<name>\n`

**Examples:**
```
JOIN|Alice
JOIN|Bob
```

---

### 2. ACK (Server → Client)

Confirms successful registration.

**Format:** `ACK|<message>\n`

**Examples:**
```
ACK|Welcome Alice
ACK|Welcome Bob
```

---

### 3. CALC (Client → Server)

Requests a math calculation. Operands are parsed as doubles. Supported operators: `+`, `-`, `*`, `/`

**Format:** `CALC|<operand1>|<operator>|<operand2>\n`

**Examples:**
```
CALC|12|+|8
CALC|100.5|/|4
CALC|-3|*|7
```

---

### 4. RESULT (Server → Client)

Returns the calculation result. Trailing zeros are stripped from the answer.

**Format:** `RESULT|<expression>|<answer>\n`

**Examples:**
```
RESULT|12+8|20
RESULT|100.5/4|25.125
RESULT|-3*7|-21
```

---

### 5. ERROR (Server → Client)

Reports an error to the client.

**Format:** `ERROR|<message>\n`

**Defined errors:**

| Condition | Message |
|---|---|
| Wrong number of fields in CALC | `Invalid request format` |
| Operand not a valid number | `Invalid number format` |
| Unsupported operator | `Unsupported operator: <op>` |
| Division by zero | `Division by zero` |
| Duplicate client name | `Name already in use: <name>` |
| Empty client name | `Client name cannot be empty` |
| Unrecognized message type | `Invalid request format` |

**Examples:**
```
ERROR|Division by zero
ERROR|Invalid number format
ERROR|Unsupported operator: %
```

---

### 6. QUIT (Client → Server)

Client requests disconnection. No additional fields.

**Format:** `QUIT\n`

---

### 7. BYE (Server → Client)

Server confirms disconnection. Includes session duration.

**Format:** `BYE|<message>\n`

**Examples:**
```
BYE|Session lasted 5m 32s
BYE|Session lasted 0m 8s
```

---

## Message Flow

```
Client                          Server
  |                               |
  |--- JOIN|<name> -------------->|
  |<-- ACK|<welcome> -------------|
  |                               |
  |--- CALC|op1|op|op2 ---------->|
  |<-- RESULT|expr|answer --------|
  |       (repeat as needed)      |
  |                               |
  |--- QUIT --------------------->|
  |<-- BYE|<message> -------------|
  |                               |
[closed]                       [closed]
```

---

## Edge Cases

| Scenario | Server Response |
|---|---|
| Empty message | `ERROR|Invalid request format` |
| CALC before JOIN | `ERROR|Invalid request format` |
| Missing fields in CALC | `ERROR|Invalid request format` |
| Client disconnects without QUIT | Server logs forced disconnect |

---

## Server Log Format

```
[yyyy-MM-dd HH:mm:ss] EVENT | ClientName | Details
```

**Example:**
```
[2026-03-27 14:01:12] CONNECT    | Alice | 192.168.1.5:52340
[2026-03-27 14:01:18] REQUEST    | Alice | 12 + 8 = 20
[2026-03-27 14:02:00] DISCONNECT | Alice | Duration: 1m 58s
```

---

## Version History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2026-03-31 | Gaurang | Initial release |
