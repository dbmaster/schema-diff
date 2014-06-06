alter table dbo.customers
add constraint ck_customers_rating_rule check (rating > 0)