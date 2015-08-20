create procedure dbo.usp_find_customers_new (@name varchar(100), @id int)
as
begin
	select * from dbo.customers where (@name is null) or name like @name
end