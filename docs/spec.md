# POS System for Tavuzi Gadgets - spec

Client: TAVUZI GADGETS
Idea: Point of Sale (POS) system for TAVUZI GADGETS. Electronics shop: Accessories sale, Electronics(tv, laptops, phones) sales, imei tracking, warranty management and repair services and offices on call repairs

## The business

- What is the name of your business? → TAVUZI GADGETS
- Where are you located (town and area)? → RESMA PLAZA, NAKURU, NEXT TO OGINGA ODINGA STREET, ROOM G-13
- How many branches or locations do you run? → 1
- How many staff will use the system? → 3-5
- What are the biggest problems with how it works today? → Inventory  management, sales management, record keeping and marketing

## Features in scope

### Login, users and roles

- Which roles will use the system? → Owner, Manager, Cashier / front desk
- Should some staff be blocked from things like discounts, refunds or reports? → yes
- How should staff log in? → Username and password

### Selling and checkout

- About how many sales do you make on a busy day? → 50-200
- About how many different products do you sell? → 100-1000
- Do you sell the same product in different units (e.g. pieces and cartons)? → yes
- Do you have different prices for some customers (e.g. wholesale and retail)? → yes
- Do cashiers give discounts? → yes
- Should cashiers be able to pause a sale and serve the next customer? → yes
- Must selling continue when the internet is down? → yes
- Do you require a feature to track and manage warranty information for electronics sold? → yes
- Will your customers be able to return or exchange products, and if so, how should the system handle these returns? → No exchanges unless supervised by me

### Payments

- How do your customers pay? → Cash, M-Pesa Till (Buy Goods), Card, M-Pesa Paybill
- Which M-Pesa account do you have? → Both
- How should M-Pesa payments reach the system? → Cashier types the M-Pesa code
- How do you take card payments? → Bank card machine (not connected)
- Do customers sometimes pay one sale with two methods (e.g. part cash, part M-Pesa)? → yes

### Tax and receipts

- Is the business registered for VAT? → yes
- What is your KRA eTIMS situation? → Need eTIMS built into the system
- What must appear on your receipts (e.g. KRA PIN, till number, return policy)? → Paybill number/till number, return policy

### Hardware

- Which devices will run the till? → Desktop computer, Laptop, Android phone
- Which receipt printer do you have or prefer? → None yet - please advise

### Stock

- Should the system track stock levels as you sell? → yes
- Do you want to record purchases from suppliers (and what you owe them)? → yes
- Do you want alerts when stock is running low? → yes
- How often do you count stock? → Weekly
- Will you need to track stock levels for warranty parts and accessories? → yes

### Customers and messages

- Do you want to keep customer records (name, phone, purchases)? → yes
- Should customers get an SMS receipt? → yes
- Do you want to send promotional SMS to customers? → yes

### M-Pesa payments

- How do your customers pay? → Cash, M-Pesa Till (Buy Goods), Card, M-Pesa Paybill
- How should M-Pesa payments reach the system? → Cashier types the M-Pesa code
- Which reports do you need? → Daily sales summary, Profit per product, Sales per cashier, Stock value, Best and slow sellers, M-Pesa reconciliation, VAT report

### KRA eTIMS invoices

- What is your KRA eTIMS situation? → Need eTIMS built into the system

### Works offline and syncs later

- Must selling continue when the internet is down? → yes

### SMS messages

- Should customers get an SMS receipt? → yes
- Do you want to send promotional SMS to customers? → yes

### Admin dashboard and reports

- Which reports do you need? → Daily sales summary, Profit per product, Sales per cashier, Stock value, Best and slow sellers, M-Pesa reconciliation, VAT report
- Do you need to export reports? → PDF
- Should each cashier close their shift with a cash count? → yes

## Stack

- backend: Java 21 + Spring Boot 3
- frontend: React + Vite + TypeScript (web frontend)

## Architecture

- Where it runs: A computer at the client. Runs on one computer at the client, reached by other devices on the local network. Include a simple backup to USB/cloud.
- Internet: Needs internet. Online only; show a clear message when the connection drops.
- Database: PostgreSQL. PostgreSQL database.

## Milestones

1. Project setup, login and roles (about 35 min)
2. Selling and checkout, Payments, Tax and receipts (about 1 h 15 min)
3. Hardware, Stock, Customers and messages (about 50 min)
4. M-Pesa payments, KRA eTIMS invoices, Works offline and syncs later (about 1 h 45 min)
5. SMS messages (about 20 min)
6. Admin dashboard and reports (about 20 min)

## Added features

- Social media: link Facebook Page, Instagram, TikTok and WhatsApp Business, and post a picture with a headline and description to the chosen accounts in one go.
