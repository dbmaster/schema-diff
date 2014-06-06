alter procedure dbo.usp_find_customers (@name varchar(100), @id int)
AS
begin
	select *
	from dbo.customers 
	where (@name is null) or name like @name 
	   or (@id is null) or customer_id=@id
end