drop index dbo.customers.idx_customers_sales

create nonclustered index idx_customers_sales on dbo.customers (rating, sales_amt)