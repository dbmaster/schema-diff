alter procedure dbo.usp_find_customers (@name varchar(100))
as
begin
	select * from dbo.customers where (@name is null) or name like @name
end