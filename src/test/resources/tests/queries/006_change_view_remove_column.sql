alter view dbo.vw_customers
as
select name,
	   sales_amt 
from dbo.customers where customer_id>100