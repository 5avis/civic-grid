# CivicGrid — Municipal Smart Street Light Controller & ERP Accounting System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![MariaDB](https://img.shields.io/badge/MariaDB-13.0-003545?logo=mariadb&logoColor=white)](https://mariadb.org/)
[![UI Style](https://img.shields.io/badge/UI%20Style-Windows%20XP%20Olive%20Skeuomorphic-4A6B46)](https://github.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**CivicGrid** is a smart municipal infrastructure management and enterprise accounting platform. It combines real-time street light grid operations, automated IoT telemetry simulation, fault lifecycle tracking, and a double-entry municipal ERP accounting engine, presented in a **Windows XP Olive Green skeuomorphic desktop interface**.

---

## Key Highlights

- **Smart Lighting Operations**: Centralized pole registry, real-time dimming control (0%–100%), power draw telemetry, and zone allocation.
- **Automated Night-Time Fault Simulator**: A background `@Scheduled` engine that detects field lamp burns (power draw = 0.0W while dimming > 0%) and generates field tickets automatically.
- **Municipal Double-Entry ERP**: Chart of Accounts, Journal Entries, real-time Balance Sheet (Assets = Liabilities + Equity), Profit & Loss statements, and Zone Budget variance tracking.
- **Role-Based Access Control (RBAC)**: Enforced segregation of duties between **Administrator (Grid Ops)** and **Accountant (Finance)**.
- **Windows XP Olive Skeuomorphic UI**: Authentic 3D beveled chrome, sunken list views, taskbars, live status bar clock, and retro system dialogs with zero emojis and vector graphics.
- **In-Page Windows XP Dialog Engine**: Custom in-page Windows XP MessageBox system replacing disruptive browser alert popups.

---

## System Architecture

```
+-------------------------------------------------------------------------+
|                  CivicGrid Windows XP Olive Web Client                  |
|  - Street Lights  - Fault Tickets  - ERP Ledger  - Reports  - Telemetry |
+-------------------------------------------------------------------------+
                                   | REST API (JSON)
                                   v
+-------------------------------------------------------------------------+
|                      Spring Boot 4.1.1 Application                      |
|  +--------------------+  +--------------------+  +--------------------+ |
|  | StreetLightService |  | FaultTicketService |  | AccountingService  | |
|  +--------------------+  +--------------------+  +--------------------+ |
|  | PowerSimulatorEngine (@Scheduled 5,000ms Automated Telemetry Loop) | |
+-------------------------------------------------------------------------+
                                   | Spring Data JPA / Hibernate
                                   v
+-------------------------------------------------------------------------+
|                         MariaDB 13.0 Database                           |
|  street_lights | zones | fault_tickets | budgets | journal_entries ...  |
+-------------------------------------------------------------------------+
```

---

## Core Modules & Features

### 1. Municipal Street Light Controller
- Register and maintain street light hardware poles with zone mapping.
- Live slider and quick-preset brightness dimming (0% Off, 50% Dusk, 80% Standard, 100% Full).
- Instant power load recalculation and live telemetry aggregation.

### 2. Automated Fault Management & Telemetry Simulation
- `@Scheduled` simulation loop running every 5,000 ms to emulate municipal night-time telemetry.
- **Automated Detection Rule**: If a light has `dimming > 0%` but draws `0.0W`, a hardware failure ticket is automatically raised.
- **Two-State Resolution Toggle**: Administrators can switch fault ticket status between `Resolved` and `Not Resolved` directly from the grid view.

### 3. ERP Municipal Accounting & Financial Reporting
- **Double-Entry General Ledger**: Automated debit and credit validation.
- **Real-Time Financial Statements**:
  - **Balance Sheet**: Assets vs. Liabilities & Operational Equity with variance check.
  - **Profit & Loss**: Municipal fees/revenue vs. electricity expenditure and repair contracts.
  - **Zone Budget Variance**: Tracks annual allocated funds vs. actual electricity spend per zone with utilization progress bars.
- **Multi-Currency Support**: Instant currency conversion across INR (₹), USD ($), EUR (€), and GBP (£).

### 4. Role-Based Access Control (RBAC) & Security
- **Mandatory Logon on Startup**: Every page open/reload requires authentication; background workspace is completely obscured until valid credentials are provided.
- **Administrator Role (Admin / Grid Ops)**: Full privileges to register lights, adjust brightness, resolve/reopen fault tickets, and post ledger transactions.
- **Accountant Role (Finance Only)**: Read-only access to street light hardware and fault tickets; full access to journal entries, ledgers, and financial statements.
- **Protected User Switching**: Toolbar role switcher requires password re-authentication before elevating privileges.

### 5. Authentic Windows XP Skeuomorphic UI
- **Windows XP Olive Green Palette**: Carefully tuned `#4a6b46` desktop radial gradient, `#ece9d8` dialog bodies, and `#716f64` 3D bevels.
- **Tabs & Windows**: Active tabs blend seamlessly into content with crisp borders and subtle shadows.
- **In-Page Message Box Engine**: Replaces native browser `alert()` popups with authentic Windows XP dialog boxes (`#modal-msgbox`).
- **Responsive Layout**: Report tables expand naturally to fit contents with zero unnecessary internal scrollbars, scrolling only when content exceeds the page length.

---

## Database Schema Overview

| Table Name | Description |
| :--- | :--- |
| `street_lights` | Light poles, operational status, zone assignment, dimming percentage, and wattage. |
| `zones` | Municipal city zones (Highway, Commercial, Residential, Industrial). |
| `fault_tickets` | Auto-generated and manual field tickets with fault types and resolution states. |
| `chart_of_accounts`| Complete municipal accounting chart (Assets 1000s, Liabilities 2000s, Equity 3000s, Revenue 4000s, Expenses 5000s). |
| `journal_entries` | General ledger transactions with balanced debits and credits. |
| `budgets` | Annual electricity budget allocations and fiscal year targets per municipal zone. |
| `contacts` | Municipal contractors, power utilities, and government administrative entities. |

---

## Getting Started

### Prerequisites
- **Java**: OpenJDK 21 or higher
- **Build Tool**: Apache Maven 3.9+
- **Database**: MariaDB 10.5+ / 13.0 or MySQL 8.0+

### Database Configuration
Update `src/main/resources/application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/civic_grid?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8
spring.datasource.username=civic_user
spring.datasource.password=civic_pass
spring.jpa.hibernate.ddl-auto=update
```

### Build & Run
```bash
# Clone repository
git clone https://github.com/5avis/civic-grid.git
cd civic-grid

# Build project with Maven
mvn clean package -DskipTests

# Run the Spring Boot application
java -jar target/civic-grid-0.0.1-SNAPSHOT.jar
```

Access the application in your browser at:
```
http://localhost:8080/
```

---

## Default Access & Roles

| Role | Access Scope | Fault Tickets | Brightness / Registration |
| :--- | :--- | :--- | :--- |
| **Administrator** | Full Grid Ops & Accounting | Resolve / Reopen | Full Control (0–100%) |
| **Accountant** | Accounting & Reports | Read-Only | Read-Only |

---

## Project Structure

```
civic-grid/
├── src/
│   ├── main/
│   │   ├── java/com/civicgrid/
│   │   │   ├── controller/      # REST API Controllers (Lights, Faults, Accounting, Reports)
│   │   │   ├── model/           # JPA Entities (StreetLight, FaultTicket, JournalEntry, Account)
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── service/         # Business Logic & Double-Entry Accounting Engine
│   │   │   └── simulation/      # Automated Night-time Telemetry & Fault Detector (@Scheduled)
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/
│   │           ├── css/xp.css   # Windows XP Olive / Luna Skeuomorphic Stylesheet
│   │           ├── js/app.js    # Client Controller, RBAC Engine & In-Page Dialogs
│   │           ├── icons8-info.svg
│   │           └── index.html   # Main Windows XP Desktop Application Interface
├── pom.xml
└── README.md
```

---

## License

This project is licensed under the [MIT License](LICENSE).
