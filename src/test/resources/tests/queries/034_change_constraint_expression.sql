alter table dbo.customers drop constraint ck_customers_sales_positive

alter table dbo.customers
add constraint ck_customers_sales_positive check (sales_amt>0 and sales_amt<1000000000)