<div align="center">

<h1>🔌 Unit Billing Plugins</h1>
<p><em>Modular plugin ecosystem for the Unit Billing platform — payments, notifications, and beyond</em></p>

  <p>
    <a href="https://github.com/m000gg/unit-billing-plugins/releases/latest"><img src="https://img.shields.io/github/v/release/m000gg/unit-billing-plugins?sort=semver" alt="Latest release"></a>
    <a href="https://github.com/m000gg/unit-billing-plugins/issues"><img src="https://img.shields.io/github/issues/m000gg/unit-billing-plugins.svg" alt="Issues"></a>
    <a href="https://github.com/m000gg/unit-billing-plugins/network/members"><img src="https://img.shields.io/github/forks/m000gg/unit-billing-plugins.svg" alt="Forks"></a>
    <a href="https://github.com/m000gg/unit-billing-plugins/blob/main/LICENSE"><img src="https://img.shields.io/github/license/m000gg/unit-billing-plugins.svg" alt="License"></a>
  </p>

</div>

---

## Contents
* *[About this project](#about-this-project)*
* *[Available Plugins](#available-plugins)*
* *[Project Structure](#project-structure)*
* *[Architecture Overview](#architecture-overview)*
* *[Technology Stack](#technology-stack)*
* *[Getting Started](#-getting-started)*
* *[Authors](#-authors)*
* *[Questions](#questions)*
* *[License](#license)*

---

## About this project
This repository hosts optional plugins that extend the core [Unit Billing](https://github.com/m000gg/unit-billing) platform with third-party integrations — payment gateways, notification channels, and other pluggable capabilities.
Each plugin is an independently buildable Maven module, allowing deployments to include only the integrations they actually need.

---

## Available Plugins

Plugins are grouped by category, each living in its own Maven module:

* **`payment-plugins/`** — gateway integrations for external payment providers (e.g. Stripe, PayPal).
* **`notification-plugins/`** — channels for delivering subscriber/admin notifications (e.g. Telegram).

> This repository is under active development. The initial modules are scaffolds — actual integration logic and the final plugin set will land in later phases.

---

## Project Structure

```text
unit-billing-plugins/
├─ pom.xml                       ← parent POM: manages Spring Boot version & shared dependencies
├─ README.md
├─ .gitignore
│
├─ payment-plugins/
│  ├─ stripe-gateway/
│  │  ├─ src/main/java/.../stripe/
│  │  ├─ src/main/resources/application-stripe.yml
│  │  └─ pom.xml
│  │
│  └─ paypal-gateway/
│     ├─ src/main/java/.../paypal/
│     ├─ src/main/resources/application-paypal.yml
│     └─ pom.xml
│
└─ notification-plugins/
   └─ telegram-notifier/
      ├─ src/main/java/.../telegram/
      └─ pom.xml
```

---

## Architecture Overview
Plugins are organized as a multi-module Maven build, grouped by category (`payment-plugins`, `notification-plugins`). The parent POM centralizes Spring Boot version management and shared dependencies so individual modules stay lightweight. Each plugin builds and packages independently and is designed to be dropped into a Unit Billing deployment without pulling in unrelated integrations.

---

## Technology Stack

| Category           | Technologies      |
|---------------------|--------------------|
| Backend             | Java, Spring Boot |
| Build Tool          | Maven (multi-module) |
| Version Control     | Git, GitHub        |

---

## ⚡ Getting Started

### 1) Clone the repo
```bash
git clone https://github.com/m000gg/unit-billing-plugins.git
cd unit-billing-plugins
```

### 2) Requirements
- Java 21+
- Maven 3.9+

### 3) Build all plugins
```bash
mvn clean package
```

### 4) Build a single plugin
```bash
mvn clean package -pl payment-plugins/stripe-gateway -am
```

Built artifacts (`.jar`) for each module will be available under that module's `target/` directory.

---

## 👥 Authors

* **m000gg** — *Core Development* — [GitHub](https://github.com/m000gg)
* **amatskevych** — *Project Lead / Mentoring* — [GitHub](https://github.com/amatskevych)

---

## Questions?

Open an Issue in this repo with a short description and steps to reproduce.
For general questions or networking, see contact links in my overview [profile](https://github.com/m000gg "m000gg profile").

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.