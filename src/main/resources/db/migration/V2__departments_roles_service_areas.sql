-- =====================================================================
-- V2: Departamentos e perfis (Officer / Approver), Service Areas geridas no sistema,
--     passos de aprovacao atribuidos a um perfil (qualquer aprovador do departamento) ou a uma pessoa.
-- =====================================================================
-- NOTA: nao usar SET NOCOUNT ON aqui. O Flyway corre na mesma ligacao do pool (Hikari) que a aplicacao usa depois;
-- com NOCOUNT ON os INSERT do Hibernate falham ("A result set was generated for update") ate a aplicacao reiniciar.
SET NOCOUNT OFF;

-- ---------- Departamentos ----------
-- has_officers = 1: o departamento tem Officers (preparam) e Approvers (aprovam). Caso contrario so Approvers.
CREATE TABLE dbo.department (
    department_id INT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(20) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    has_officers BIT NOT NULL DEFAULT 0,
    is_system BIT NOT NULL DEFAULT 0,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_department PRIMARY KEY (department_id),
    CONSTRAINT uq_department_code UNIQUE (code),
    CONSTRAINT uq_department_name UNIQUE (name),
    CONSTRAINT ck_department_code CHECK (code NOT LIKE '%[^A-Z0-9_]%')
);

-- Perfis passam a pertencer a um departamento e a ter um nivel
ALTER TABLE dbo.app_role ADD
    department_id INT NULL,
    role_level NVARCHAR(10) NOT NULL CONSTRAINT df_app_role_level DEFAULT 'VIEWER',
    is_active BIT NOT NULL CONSTRAINT df_app_role_active DEFAULT 1;
GO

ALTER TABLE dbo.app_role ADD
    CONSTRAINT fk_app_role_department_id FOREIGN KEY (department_id) REFERENCES dbo.department(department_id),
    CONSTRAINT ck_app_role_level CHECK (role_level IN ('ADMIN','OFFICER','APPROVER','VIEWER'));
CREATE UNIQUE INDEX ux_app_role_department_level ON dbo.app_role(department_id, role_level) WHERE department_id IS NOT NULL;

-- Departamentos do BRD (seccao 7 - Stakeholders & Roles). CS e LEGAL tem Officers + Approver; os outros so Approvers.
INSERT INTO dbo.department(code, name, has_officers, is_system) VALUES
    ('CS', 'Corporate Services', 1, 1),
    ('LEGAL', 'Legal', 1, 1),
    ('RISK', 'Risk & Compliance', 0, 0),
    ('COMPLIANCE', 'Compliance (AML & Regulatory)', 0, 0),
    ('IT', 'CISO / IT', 0, 0),
    ('AUDIT', 'Internal Audit', 0, 0),
    ('CRO', 'Chief Risk Officer', 0, 0),
    ('MD', 'Managing Director', 0, 0);

-- Perfis existentes (V1) sao convertidos, mantendo os ids (user_role continua valido)
UPDATE dbo.app_role SET role_level = 'ADMIN' WHERE code = 'SYS_ADMIN';
UPDATE dbo.app_role SET role_level = 'VIEWER', name = 'UBA Staff (read-only)' WHERE code = 'UBA_STAFF';
UPDATE r SET department_id = d.department_id, role_level = 'OFFICER', name = 'Corporate Services Officer'
    FROM dbo.app_role r JOIN dbo.department d ON d.code = 'CS' WHERE r.code = 'CS_OFFICER';
UPDATE r SET code = 'CS_APPROVER', department_id = d.department_id, role_level = 'APPROVER', name = 'Corporate Services Approver'
    FROM dbo.app_role r JOIN dbo.department d ON d.code = 'CS' WHERE r.code = 'APPROVER';
UPDATE r SET code = 'COMPLIANCE_APPROVER', department_id = d.department_id, role_level = 'APPROVER', name = 'Compliance (AML & Regulatory) Approver'
    FROM dbo.app_role r JOIN dbo.department d ON d.code = 'COMPLIANCE' WHERE r.code = 'COMPLIANCE';
UPDATE r SET code = 'RISK_APPROVER', department_id = d.department_id, role_level = 'APPROVER', name = 'Risk & Compliance Approver'
    FROM dbo.app_role r JOIN dbo.department d ON d.code = 'RISK' WHERE r.code = 'RISK_TEAM';

-- Perfis em falta: um APPROVER por departamento e um OFFICER onde has_officers = 1
INSERT INTO dbo.app_role(code, name, department_id, role_level)
    SELECT d.code + '_APPROVER', d.name + ' Approver', d.department_id, 'APPROVER' FROM dbo.department d
    WHERE NOT EXISTS (SELECT 1 FROM dbo.app_role r WHERE r.department_id = d.department_id AND r.role_level = 'APPROVER');
INSERT INTO dbo.app_role(code, name, department_id, role_level)
    SELECT d.code + '_OFFICER', d.name + ' Officer', d.department_id, 'OFFICER' FROM dbo.department d
    WHERE d.has_officers = 1 AND NOT EXISTS (SELECT 1 FROM dbo.app_role r WHERE r.department_id = d.department_id AND r.role_level = 'OFFICER');

-- ---------- Service Areas (geridas em Settings > Service Areas) ----------
ALTER TABLE dbo.service_category ADD description NVARCHAR(300) NULL;

-- ---------- Passos do fluxo: perfil obrigatorio, pessoa opcional ----------
ALTER TABLE dbo.approval_flow_step ADD approver_role_id INT NULL;
GO

-- Passos existentes: perfil = primeiro perfil do aprovador designado (ou SYS_ADMIN)
UPDATE s SET approver_role_id = COALESCE(
        (SELECT MIN(ur.role_id) FROM dbo.user_role ur JOIN dbo.app_role r ON r.role_id = ur.role_id
          WHERE ur.user_id = s.approver_user_id AND r.role_level IN ('APPROVER','ADMIN')),
        (SELECT role_id FROM dbo.app_role WHERE code = 'SYS_ADMIN'))
    FROM dbo.approval_flow_step s;

ALTER TABLE dbo.approval_flow_step ALTER COLUMN approver_role_id INT NOT NULL;
ALTER TABLE dbo.approval_flow_step DROP CONSTRAINT fk_approval_flow_step_approver_user_id;
ALTER TABLE dbo.approval_flow_step ALTER COLUMN approver_user_id BIGINT NULL;
ALTER TABLE dbo.approval_flow_step ADD
    CONSTRAINT fk_approval_flow_step_approver_user_id FOREIGN KEY (approver_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_approval_flow_step_approver_role_id FOREIGN KEY (approver_role_id) REFERENCES dbo.app_role(role_id);

-- ---------- Tarefas: atribuidas a um perfil e/ou a uma pessoa; guarda quem decidiu ----------
DROP INDEX ix_task_inbox ON dbo.approval_task;
ALTER TABLE dbo.approval_task DROP CONSTRAINT fk_approval_task_approver_user_id;
ALTER TABLE dbo.approval_task ALTER COLUMN approver_user_id BIGINT NULL;
ALTER TABLE dbo.approval_task ADD
    approver_role_id INT NULL,
    decided_by BIGINT NULL;
GO

ALTER TABLE dbo.approval_task ADD
    CONSTRAINT fk_approval_task_approver_user_id FOREIGN KEY (approver_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_approval_task_approver_role_id FOREIGN KEY (approver_role_id) REFERENCES dbo.app_role(role_id),
    CONSTRAINT fk_approval_task_decided_by FOREIGN KEY (decided_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_approval_task_assignee CHECK (approver_user_id IS NOT NULL OR approver_role_id IS NOT NULL);

-- Tarefas ja decididas: quem decidiu foi o aprovador designado
UPDATE dbo.approval_task SET decided_by = approver_user_id WHERE decision IN ('APPROVED','REJECTED','RETURNED');

CREATE INDEX ix_task_inbox ON dbo.approval_task(approver_user_id) WHERE decision = 'PENDING';
CREATE INDEX ix_task_role_inbox ON dbo.approval_task(approver_role_id) WHERE decision = 'PENDING';

-- Reatribuicao de uma tarefa ainda sem pessoa (so perfil): from_user_id fica NULL
ALTER TABLE dbo.approval_task_reassignment DROP CONSTRAINT ck_approval_task_reassignment_diff;
ALTER TABLE dbo.approval_task_reassignment DROP CONSTRAINT fk_approval_task_reassignment_from_user_id;
ALTER TABLE dbo.approval_task_reassignment ALTER COLUMN from_user_id BIGINT NULL;
ALTER TABLE dbo.approval_task_reassignment ADD
    CONSTRAINT fk_approval_task_reassignment_from_user_id FOREIGN KEY (from_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_approval_task_reassignment_diff CHECK (from_user_id IS NULL OR from_user_id <> to_user_id);
GO

-- Repoe o estado da sessao (o V1 deixa NOCOUNT ON na ligacao do pool)
SET NOCOUNT OFF;
GO
