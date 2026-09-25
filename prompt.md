PROJECT: CivicGrid — Municipal Smart Street Light Controller + ERP Accounting System

ROLE: You are my AI coding assistant (Antigravity CLI) working inside my IntelliJ project. I am a beginner-level developer learning as I build this. Follow the instructions below exactly.

====================================================
0. FIRST STEP — UNDERSTAND CURRENT PROJECT
====================================================
Before writing or changing anything, inspect my current project setup:
- Folder structure (I already have a Spring Boot + Maven project set up: package com.example.civicgrid)
- pom.xml — check which dependencies are already present
- application.properties
Tell me what you found. Then ask me for my MariaDB connection details (host, port, database name, username, password) before proceeding.

====================================================
1. PROJECT SCOPE (STRICT — DO NOT DEVIATE)
====================================================
Build ONLY what is described below. Do NOT add extra features, "nice-to-haves," authentication systems, UI polish, third-party integrations, payment gateways, or real sensor/hardware integration. If anything seems missing or unclear, ASK ME before adding it — never assume or expand scope on your own. Every piece of code must be explainable — I should understand why it exists, not just copy-paste it.

This is BACKEND + DATABASE only. No frontend/UI.

====================================================
2. PROJECT OVERVIEW
====================================================
A municipal smart street lighting system where:
- Admin manages light poles, groups them into zones, and controls dimming
- The system simulates power draw internally and auto-creates a fault ticket if a light shows 0 power while it should be on at night
- Accountant manages billing: pays the Electric Utility and Repair Contractor, invoices the Municipality, records payments
- Budgets are tracked per zone (planned vs actual spend)
- Financial reports are generated (Balance Sheet, P&L, Budget Variance Report)

No real hardware, no real bank/UPI integration. Power draw is SIMULATED using an internal scheduled job (e.g. Spring's @Scheduled), NOT via an external API call.

====================================================
3. ACTORS
====================================================
- Admin (Grid Operations Director): configures pole grids/zones, sets dimming, manages budgets
- Invoicing User (Utility Accountant): manages vendor bills, customer invoices, payments
- System (automated): simulates power draw, auto-generates fault tickets, updates cost ledgers

Keep these roles conceptually separated in the code (even without full auth) — don't mix Admin and Accountant logic together.

====================================================
4. DATABASE (MariaDB)
====================================================
I will give you the connection details when asked. Use Spring Data JPA to create these entities/tables (only these — nothing extra):
1. contacts (type: vendor/customer — Electric Utility, Repair Contractor, Municipality)
2. products (LED Pole - Good, Electricity kWh - Good, Repair Service - Service)
3. chart_of_accounts (Assets, Liabilities, Income, Expenses)
4. zones (e.g. Highway Zone 7)
5. street_lights (linked to a zone)
6. journals (Purchase Journal, Sales Journal, Bank/Cash Journal)
7. journal_entries (double-entry: debit/credit records)
8. purchase_orders
9. vendor_bills
10. sales_orders
11. customer_invoices
12. payments
13. budgets (per zone: planned vs actual)
14. fault_tickets

====================================================
5. TRANSACTION FLOW (double-entry accounting)
====================================================
- Purchase Order → Vendor Bill → Payment (money OUT to Electric Utility / Repair Contractor)
- Sales Order → Customer Invoice → Payment (money IN from Municipality)
- Every financial transaction must create a matching journal entry with balanced debit and credit

Example:
- Receiving electricity bill: Debit Electricity Expense, Credit Power Utility Payable
- Paying that bill: Debit Power Utility Payable, Credit Cash/Bank
- Receiving municipality payment: Debit Cash/Bank, Credit Municipal Grid Service Fee (Income)

====================================================
6. LIGHT CONTROL & FAULT DETECTION
====================================================
- Dimming (0-100%) is a value the Admin SETS manually (not a fault indicator by itself)
- Power draw (watts) is SIMULATED internally via a scheduled backend job (@Scheduled) — generate a value periodically, do NOT route this through an external API call
- Fault ticket logic: IF it is night hours AND a light's dimming > 0% (should be on) AND simulated power draw = 0 → automatically create a fault_ticket record

====================================================
7. BUDGET
====================================================
- Each zone has a planned budget (set by Admin)
- System tracks actual spend per zone (from vendor bills/expenses linked to that zone)
- Provide a way to compare planned vs actual

====================================================
8. REPORTS
====================================================
Generate these from journal entries/database:
1. Balance Sheet — assets (infrastructure, cash) vs liabilities (payables)
2. Profit & Loss — income (service fee) minus expenses (electricity + repair)
3. Budget Report — zone-wise planned vs actual variance

====================================================
9. APIs (only these 3)
====================================================
1. POST /api/street-lights — register a new light pole (assign to a zone)
2. PUT /api/street-lights/{id}/dimming — manually set a light's brightness
3. GET /api/street-lights/power-summary — return total power usage summary

====================================================
10. DATA ENTRY PAGE (backend endpoint, no UI)
====================================================
Provide a simple endpoint for the Accountant to submit transaction details (type: Vendor Bill / Customer Invoice / Payment, amount, date, party, account) — this creates the corresponding journal entry.

====================================================
11. HOW I WANT YOU TO WORK WITH ME
====================================================
Build in STAGES, in this exact order. Do not skip ahead or combine stages:
  Stage 1: Database connection + entity/table creation
  Stage 2: Master data CRUD (contacts, products, chart of accounts, zones)
  Stage 3: Transaction logic (PO → Bill → Payment, Sales Order → Invoice → Payment) with double-entry journal creation
  Stage 4: Dimming + simulated power draw (@Scheduled) + fault ticket logic
  Stage 5: Budget tracking
  Stage 6: Reports
  Stage 7: The 3 REST APIs

After each stage:
- Tell me exactly what manual step I need to take (e.g. "run this Maven command," "check this table in MariaDB," "restart the app") — I want to do my part, not have everything happen silently.
- Give me a clear, SHORT explanation: what we just built and why it's needed.
- Wait for my confirmation before moving to the next stage.

Confirm you understand this full scope, then tell me what you need from me to begin Stage 1.
