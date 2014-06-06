alter table dbo.customers drop constraint df_rating_five

alter table dbo.customers 
add constraint df_rating_five  default 6.0 for rating