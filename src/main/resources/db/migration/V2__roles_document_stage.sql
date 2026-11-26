-- V2: perfil Legal e fase (estado da entidade) em que cada documento foi submetido
ALTER TABLE dbo.document ADD stage NVARCHAR(20) NULL;
GO

INSERT INTO dbo.app_role(code, name) VALUES ('LEGAL', 'Legal Officer');
GO
