drop index dbo.customers.idx_customers_sales

create nonclustered index idx_customers_sales on dbo.customers (sales_amt desc, rating desc)