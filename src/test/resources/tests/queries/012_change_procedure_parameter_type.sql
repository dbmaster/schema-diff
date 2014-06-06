-- name became varchar(200) instead of varchar(100)
alter procedure dbo.usp_find_customers (@name varchar(200), @id int)
as
begin
	select * from dbo.customers where (@name is null) or name like @name
end