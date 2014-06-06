

    IF EXISTS (SELECT name FROM master.dbo.sysdatabases WHERE name = N'034_change_constraint_expression.sql')
    BEGIN
        DROP DATABASE [034_change_constraint_expression.sql];
    END
    
    CREATE DATABASE [034_change_constraint_expression.sql]
 ON 
 PRIMARY 
( NAME = N'data_file', FILENAME = N'C:\temp\databases\034_change_constraint_expression.sql.mdf' , 
   SIZE = 4096KB , MAXSIZE = UNLIMITED, FILEGROWTH = 1024KB )
 LOG ON 
( NAME = N'log_file',  FILENAME = N'C:\temp\databases\034_change_constraint_expression.sql.ldf' , 
  SIZE = 1024KB , MAXSIZE = 2048GB , FILEGROWTH = 10%)