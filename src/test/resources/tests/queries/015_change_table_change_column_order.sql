drop table dbo.customers

;

create table dbo.customers (
    customer_id int NOT NULL,
    name varchar(50) NOT NULL,
    debt_amt decimal(6,2) NULL,
    sales_amt int NULL CONSTRAINT ck_customers_sales_positive CHECK (sales_amt > 0),
    rating decimal(6, 2) NULL CONSTRAINT df_rating_five DEFAULT 5.0,
    constraint pk_customer primary key clustered (customer_id asc)
)
;

create nonclustered index idx_customers_sales on dbo.customers (sales_amt, rating)
;
