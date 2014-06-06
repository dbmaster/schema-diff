alter view dbo.vw_customers
as
select * from dbo.customers where customer_id < 120