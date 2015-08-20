create view dbo.vw_customers_new
as
select * from dbo.customers where customer_id>1000