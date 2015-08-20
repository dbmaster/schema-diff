alter view dbo.vw_customers
as
select name,
       sales_amt,
       customer_id       
from dbo.customers where customer_id>100