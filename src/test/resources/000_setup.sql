----------------- setup ------------------------------------

create table dbo.customers (
    customer_id int NOT NULL,
    name varchar(50) NOT NULL,
    sales_amt int NULL CONSTRAINT ck_customers_sales_positive CHECK (sales_amt > 0),
    rating decimal(6, 2) NULL CONSTRAINT df_rating_five DEFAULT 5.0,
    debt_amt decimal(6,2) NULL
    constraint pk_customer primary key clustered (customer_id asc)
)
;


create nonclustered index idx_customers_sales on dbo.customers (sales_amt, rating)
;

create view dbo.vw_customers
as
select customer_id,
       name,
       sales_amt
from dbo.customers where customer_id>100

;

create procedure dbo.usp_find_customers (@name varchar(100), @id int)
as
begin
    select * from dbo.customers where (@name is null) or name like @name
end

;
--- Sample of long text procedure
create procedure dbo.CalMonthByYearMonth (@YEAR INT, @MONTH INT) AS
    DECLARE @INPUTDATE DATETIME
    DECLARE @DATE DATETIME
    DECLARE @LASTDATE DATETIME
    DECLARE @MONTHDAYCOUNT INT
    DECLARE @COUNT INT
    DECLARE @DAY VARCHAR(10)
    DECLARE @STARTWEEK INT
    DECLARE @CURWEEK INT
    DECLARE @STARTMONTH INT
    SET @INPUTDATE='01/01/' + CAST(@YEAR AS CHAR(4))
    PRINT @INPUTDATE
    SET @STARTMONTH=@MONTH
    SET @INPUTDATE=DATEADD(MM,@MONTH - 1,@INPUTDATE)

    SET @COUNT=1
    SET @DATE = DATEADD(d, -(DATEPART(dd, @INPUTDATE) - 1), @INPUTDATE)
    SET @LASTDATE=DATEADD(DD,-1,DATEADD(MM,1,@DATE))
    SET @MONTHDAYCOUNT=datediff(d, @date, dateadd(m, 1, @date))
    SET @STARTWEEK=DATEPART(WEEK,@INPUTDATE)
    DECLARE @CURRWEEK INT
    DECLARE @CUR INT
    CREATE TABLE #TEMP(
        WEEK VARCHAR(10),
        SUNDAY VARCHAR(10),
        MONDAY VARCHAR(10),
        TUESDAY VARCHAR(10),
        WEDNESDAY VARCHAR(10),
        THURSDAY VARCHAR(10),
        FRIDAY VARCHAR(10),
        SATURDAY VARCHAR(10),
        YEARWEEK VARCHAR(10))
    DECLARE @wkcount int
    DECLARE @weeksinmonth int
    DECLARE @EXEC NVARCHAR(2000)
    SET @WKCOUNT=1
    SET @weeksinmonth=datediff(week, @date, @lastdate) + 1
    WHILE @wkcount<= @weeksinmonth
        begin
        INSERT INTO #TEMP VALUES(@wkcount,'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', @STARTWEEK + @WKCOUNT - 1)
        SET @WKCOUNT=@WKCOUNT + 1
    end
    WHILE @COUNT<=@MONTHDAYCOUNT
        BEGIN
                SET @DAY=DATENAME(WEEKDAY,@DATE)
                IF @STARTWEEK=DATENAME(WEEK,@DATE)
                        SET @CURRWEEK=1
                ELSE
                BEGIN
                        SET @CUR=DATENAME(WEEK,@DATE)
                        SET @CURRWEEK=(@CUR-@STARTWEEK)+1
                END

                SET @EXEC='UPDATE #TEMP SET ' + @DAY + ' =' + CAST(@COUNT AS CHAR(2)) + ' WHERE WEEK=' + CAST(@CURRWEEK AS CHAR(2))+ 'AND WEEK IS NOT NULL'
                EXEC SP_EXECUTESQL @EXEC
                SET @DATE=DATEADD(DD,1,@DATE)
                SET @COUNT=@COUNT + 1
    END
    UPDATE #TEMP SET SUNDAY=' ' WHERE SUNDAY='SUNDAY'
    UPDATE #TEMP SET MONDAY=' ' WHERE MONDAY='MONDAY'
    UPDATE #TEMP SET TUESDAY=' ' WHERE TUESDAY='TUESDAY'
    UPDATE #TEMP SET WEDNESDAY=' ' WHERE WEDNESDAY='WEDNESDAY'
    UPDATE #TEMP SET THURSDAY=' ' WHERE THURSDAY='THURSDAY'
    UPDATE #TEMP SET FRIDAY=' ' WHERE FRIDAY='FRIDAY'
    UPDATE #TEMP SET SATURDAY=' ' WHERE SATURDAY='SATURDAY'
    SELECT Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday --, YEARWEEK
    --SELECT Monday, Tuesday, Wednesday, Thursday, Friday, YEARWEEK
    FROM #TEMP order by week
    DROP TABLE #TEMP
;

-- select * from sys.all_objects where is_ms_shipped<>1