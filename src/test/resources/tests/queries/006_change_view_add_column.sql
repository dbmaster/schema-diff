alter view dbo.vw_customers
as
select customer_id,
       name,
       sales_amt,
       last_updated=CURRENT_TIMESTAMP
from dbo.customers where customer_id>100