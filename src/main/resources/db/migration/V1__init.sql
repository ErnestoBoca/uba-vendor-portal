-- =====================================================================
-- VMS - UBA Vendor Portal | v4 | SQL Server (T-SQL)
-- Ambito: login, vendor onboarding, contratos, aprovacoes (com reassign), documentos, auditoria
-- Compativel com Flyway: colocar em src/main/resources/db/migration/V1__init.sql
-- (o 'GO' e' tratado pelo Flyway; em SSMS/sqlcmd tambem funciona)
-- Regras de negocio (workflow, activacao, auditoria) ficam no servico Spring Boot, nao em triggers.
-- =====================================================================
SET NOCOUNT ON;

-- Parametros do sistema (chave/valor)
CREATE TABLE dbo.system_setting (
    setting_key NVARCHAR(60) NOT NULL,
    setting_value NVARCHAR(200) NOT NULL,
    description NVARCHAR(300) NULL,
    CONSTRAINT pk_system_setting PRIMARY KEY (setting_key)
);

-- Perfis de acesso
CREATE TABLE dbo.app_role (
    role_id INT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(80) NOT NULL,
    CONSTRAINT pk_app_role PRIMARY KEY (role_id),
    CONSTRAINT uq_app_role_code UNIQUE (code)
);

-- Utilizadores internos
CREATE TABLE dbo.app_user (
    user_id BIGINT IDENTITY(1,1) NOT NULL,
    username NVARCHAR(50) NOT NULL,
    full_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(150) NOT NULL,
    password_hash NVARCHAR(200) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT pk_app_user PRIMARY KEY (user_id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE dbo.user_role (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_user_role_role_id FOREIGN KEY (role_id) REFERENCES dbo.app_role(role_id)
);

-- Tipos de entidade que podem ter documentos/aprovacoes. Novo modulo (ex.: ASSET) = 1 INSERT aqui
CREATE TABLE dbo.entity_type (
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(80) NOT NULL,
    supports_documents BIT NOT NULL DEFAULT 0,
    supports_approval BIT NOT NULL DEFAULT 0,
    CONSTRAINT pk_entity_type PRIMARY KEY (code)
);

-- Area de servico (Service Area); define o fluxo de aprovacao dos contratos
CREATE TABLE dbo.service_category (
    service_category_id INT IDENTITY(1,1) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_service_category PRIMARY KEY (service_category_id),
    CONSTRAINT uq_service_category_name UNIQUE (name)
);

-- Registo de fornecedores (so via Vendor Onboarding)
CREATE TABLE dbo.vendor (
    vendor_id BIGINT IDENTITY(1,1) NOT NULL,
    vendor_code AS ('UBA-VND-' + RIGHT('000000' + CAST(vendor_id AS VARCHAR(10)), 6)) PERSISTED,
    supplier_name NVARCHAR(200) NOT NULL,
    email NVARCHAR(150) NOT NULL,
    mobile NVARCHAR(40) NOT NULL,
    phone NVARCHAR(40) NULL,
    address NVARCHAR(300) NOT NULL,
    nuit CHAR(9) NOT NULL,
    service_category_id INT NOT NULL,
    service_description NVARCHAR(MAX) NULL,
    company_reg_number NVARCHAR(40) NULL,
    nuit_verified BIT NOT NULL DEFAULT 0,
    status NVARCHAR(12) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT NULL,
    reviewed_at DATETIMEOFFSET NULL,
    review_comment NVARCHAR(MAX) NULL,
    is_suspended BIT NOT NULL DEFAULT 0,
    suspended_reason NVARCHAR(300) NULL,
    created_by BIGINT NULL,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT pk_vendor PRIMARY KEY (vendor_id),
    CONSTRAINT uq_vendor_nuit UNIQUE (nuit),
    CONSTRAINT uq_vendor_vendor_code UNIQUE (vendor_code),
    CONSTRAINT fk_vendor_service_category_id FOREIGN KEY (service_category_id) REFERENCES dbo.service_category(service_category_id),
    CONSTRAINT fk_vendor_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_vendor_created_by FOREIGN KEY (created_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_vendor_nuit CHECK (nuit NOT LIKE '%[^0-9]%'),
    CONSTRAINT ck_vendor_status CHECK (status IN ('PENDING','UNDER_REVIEW','APPROVED','REJECTED'))
);

CREATE TABLE dbo.vendor_contact (
    contact_id BIGINT IDENTITY(1,1) NOT NULL,
    vendor_id BIGINT NOT NULL,
    full_name NVARCHAR(150) NOT NULL,
    phone NVARCHAR(40) NOT NULL,
    job_title NVARCHAR(100) NOT NULL,
    email NVARCHAR(150) NULL,
    is_primary BIT NOT NULL DEFAULT 0,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_vendor_contact PRIMARY KEY (contact_id),
    CONSTRAINT fk_vendor_contact_vendor_id FOREIGN KEY (vendor_id) REFERENCES dbo.vendor(vendor_id)
);

CREATE TABLE dbo.document_type (
    document_type_id INT IDENTITY(1,1) NOT NULL,
    entity_type NVARCHAR(30) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_document_type PRIMARY KEY (document_type_id),
    CONSTRAINT uq_document_type_entity_type_name UNIQUE (entity_type, name),
    CONSTRAINT fk_document_type_entity_type FOREIGN KEY (entity_type) REFERENCES dbo.entity_type(code)
);

-- UNICA tabela de documentos: (entity_type, entity_id) aponta para o dono (VENDOR, CONTRACT, futuramente ASSET...)
CREATE TABLE dbo.document (
    document_id BIGINT IDENTITY(1,1) NOT NULL,
    entity_type NVARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    document_type_id INT NULL,
    name NVARCHAR(200) NOT NULL,
    file_name NVARCHAR(255) NOT NULL,
    storage_key NVARCHAR(500) NOT NULL,
    mime_type NVARCHAR(100) NULL,
    file_size_bytes BIGINT NULL,
    sha256 CHAR(64) NULL,
    issue_date DATE NULL,
    expiry_date DATE NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_document PRIMARY KEY (document_id),
    CONSTRAINT fk_document_entity_type FOREIGN KEY (entity_type) REFERENCES dbo.entity_type(code),
    CONSTRAINT fk_document_document_type_id FOREIGN KEY (document_type_id) REFERENCES dbo.document_type(document_type_id),
    CONSTRAINT fk_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_document_dates CHECK (expiry_date IS NULL OR issue_date IS NULL OR expiry_date >= issue_date)
);

-- Contratos; so ficam ACTIVE depois de todo o fluxo de aprovacao
CREATE TABLE dbo.contract (
    contract_id BIGINT IDENTITY(1,1) NOT NULL,
    vendor_id BIGINT NOT NULL,
    service_category_id INT NOT NULL,
    contract_number NVARCHAR(40) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    contract_type NVARCHAR(12) NOT NULL,
    object_description NVARCHAR(MAX) NULL,
    signing_date DATE NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    value_amount DECIMAL(18,2) NULL,
    currency CHAR(3) NOT NULL DEFAULT 'MZN',
    payment_terms NVARCHAR(MAX) NULL,
    termination_notice_days SMALLINT NULL,
    penalty_clause NVARCHAR(MAX) NULL,
    governing_law NVARCHAR(100) NOT NULL DEFAULT 'Mozambique',
    sla_terms NVARCHAR(MAX) NULL,
    dpa_status NVARCHAR(12) NOT NULL DEFAULT 'NOT_REQUIRED',
    audit_rights BIT NOT NULL DEFAULT 0,
    auto_renew BIT NOT NULL DEFAULT 0,
    renewal_notice_days SMALLINT NOT NULL DEFAULT 60,
    vendor_signatory NVARCHAR(150) NULL,
    bank_signatory NVARCHAR(150) NULL,
    owner_user_id BIGINT NULL,
    status NVARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    activated_at DATETIMEOFFSET NULL,
    created_by BIGINT NULL,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT pk_contract PRIMARY KEY (contract_id),
    CONSTRAINT uq_contract_contract_number UNIQUE (contract_number),
    CONSTRAINT fk_contract_vendor_id FOREIGN KEY (vendor_id) REFERENCES dbo.vendor(vendor_id),
    CONSTRAINT fk_contract_service_category_id FOREIGN KEY (service_category_id) REFERENCES dbo.service_category(service_category_id),
    CONSTRAINT fk_contract_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_contract_created_by FOREIGN KEY (created_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_contract_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_contract_value CHECK (value_amount IS NULL OR value_amount >= 0),
    CONSTRAINT ck_contract_type CHECK (contract_type IN ('SERVICE','SUPPLY','LICENSE','CONSULTING','OTHER')),
    CONSTRAINT ck_contract_currency CHECK (currency IN ('MZN','USD','EUR','ZAR')),
    CONSTRAINT ck_contract_dpa CHECK (dpa_status IN ('NOT_REQUIRED','PENDING','SIGNED','EXPIRED')),
    CONSTRAINT ck_contract_status CHECK (status IN ('DRAFT','IN_APPROVAL','REJECTED','ACTIVE','TERMINATED','CLOSED'))
);

-- Fluxo por entidade e por ambito. CONTRACT: scope_id = service_category_id; 0 = fluxo por defeito
CREATE TABLE dbo.approval_flow (
    flow_id INT IDENTITY(1,1) NOT NULL,
    entity_type NVARCHAR(30) NOT NULL,
    scope_id BIGINT NOT NULL DEFAULT 0,
    name NVARCHAR(150) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_approval_flow PRIMARY KEY (flow_id),
    CONSTRAINT uq_approval_flow_entity_type_scope_id UNIQUE (entity_type, scope_id),
    CONSTRAINT fk_approval_flow_entity_type FOREIGN KEY (entity_type) REFERENCES dbo.entity_type(code)
);

-- Cada passo tem UMA pessoa designada
CREATE TABLE dbo.approval_flow_step (
    step_id INT IDENTITY(1,1) NOT NULL,
    flow_id INT NOT NULL,
    step_no SMALLINT NOT NULL,
    name NVARCHAR(100) NOT NULL,
    approver_user_id BIGINT NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_approval_flow_step PRIMARY KEY (step_id),
    CONSTRAINT uq_approval_flow_step_flow_id_step_no UNIQUE (flow_id, step_no),
    CONSTRAINT fk_approval_flow_step_flow_id FOREIGN KEY (flow_id) REFERENCES dbo.approval_flow(flow_id),
    CONSTRAINT fk_approval_flow_step_approver_user_id FOREIGN KEY (approver_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_approval_flow_step_stepno CHECK (step_no > 0)
);

CREATE TABLE dbo.approval_request (
    request_id BIGINT IDENTITY(1,1) NOT NULL,
    entity_type NVARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    status NVARCHAR(12) NOT NULL DEFAULT 'IN_APPROVAL',
    submitted_by BIGINT NOT NULL,
    submitted_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    closed_at DATETIMEOFFSET NULL,
    CONSTRAINT pk_approval_request PRIMARY KEY (request_id),
    CONSTRAINT fk_approval_request_entity_type FOREIGN KEY (entity_type) REFERENCES dbo.entity_type(code),
    CONSTRAINT fk_approval_request_submitted_by FOREIGN KEY (submitted_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_approval_request_status CHECK (status IN ('IN_APPROVAL','APPROVED','REJECTED','RETURNED','CANCELLED'))
);

-- Copia (snapshot) do fluxo na submissao; approver_user_id muda se houver reassign
CREATE TABLE dbo.approval_task (
    task_id BIGINT IDENTITY(1,1) NOT NULL,
    request_id BIGINT NOT NULL,
    step_no SMALLINT NOT NULL,
    step_name NVARCHAR(100) NOT NULL,
    approver_user_id BIGINT NOT NULL,
    decision NVARCHAR(10) NOT NULL DEFAULT 'WAITING',
    decided_at DATETIMEOFFSET NULL,
    comments NVARCHAR(MAX) NULL,
    CONSTRAINT pk_approval_task PRIMARY KEY (task_id),
    CONSTRAINT uq_approval_task_request_id_step_no UNIQUE (request_id, step_no),
    CONSTRAINT fk_approval_task_request_id FOREIGN KEY (request_id) REFERENCES dbo.approval_request(request_id),
    CONSTRAINT fk_approval_task_approver_user_id FOREIGN KEY (approver_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_approval_task_decision CHECK (decision IN ('WAITING','PENDING','APPROVED','REJECTED','RETURNED','CANCELLED')),
    CONSTRAINT ck_approval_task_decided CHECK (decision NOT IN ('APPROVED','REJECTED','RETURNED') OR decided_at IS NOT NULL)
);

-- Historico de reatribuicoes de um pedido (quem, de quem para quem, porque)
CREATE TABLE dbo.approval_task_reassignment (
    reassignment_id BIGINT IDENTITY(1,1) NOT NULL,
    task_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    reassigned_by BIGINT NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    reassigned_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT pk_approval_task_reassignment PRIMARY KEY (reassignment_id),
    CONSTRAINT fk_approval_task_reassignment_task_id FOREIGN KEY (task_id) REFERENCES dbo.approval_task(task_id),
    CONSTRAINT fk_approval_task_reassignment_from_user_id FOREIGN KEY (from_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_approval_task_reassignment_to_user_id FOREIGN KEY (to_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_approval_task_reassignment_reassigned_by FOREIGN KEY (reassigned_by) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_approval_task_reassignment_diff CHECK (from_user_id <> to_user_id)
);

-- Append-only. Escrita pela aplicacao (Spring); sem UPDATE/DELETE
CREATE TABLE dbo.audit_log (
    audit_id BIGINT IDENTITY(1,1) NOT NULL,
    occurred_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    actor_user_id BIGINT NULL,
    actor_ip NVARCHAR(45) NULL,
    entity_type NVARCHAR(40) NOT NULL,
    entity_id NVARCHAR(40) NULL,
    vendor_id BIGINT NULL,
    action NVARCHAR(20) NOT NULL,
    old_data NVARCHAR(MAX) NULL,
    new_data NVARCHAR(MAX) NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_log_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES dbo.app_user(user_id)
);

CREATE UNIQUE INDEX ux_vendor_primary_contact ON dbo.vendor_contact(vendor_id) WHERE is_primary = 1 AND is_active = 1;
CREATE INDEX ix_vendor_contact_vendor ON dbo.vendor_contact(vendor_id);
CREATE INDEX ix_document_owner ON dbo.document(entity_type, entity_id);
CREATE INDEX ix_contract_vendor ON dbo.contract(vendor_id);
CREATE INDEX ix_contract_status_end ON dbo.contract(status, end_date);
CREATE UNIQUE INDEX ux_request_one_open ON dbo.approval_request(entity_type, entity_id) WHERE status = 'IN_APPROVAL';
CREATE INDEX ix_task_inbox ON dbo.approval_task(approver_user_id) WHERE decision = 'PENDING';
CREATE INDEX ix_audit_vendor ON dbo.audit_log(vendor_id, occurred_at);
CREATE INDEX ix_audit_entity ON dbo.audit_log(entity_type, entity_id);

GO

INSERT INTO dbo.system_setting(setting_key, setting_value, description) VALUES ('enforce_segregation_of_duties','false','Se true, quem submete nao pode aprovar o proprio pedido (desligado enquanto so existir o login admin)'),('contract_expiry_warning_days','60','Dias antes do fim do contrato para destacar a expirar');
INSERT INTO dbo.entity_type(code, name, supports_documents, supports_approval) VALUES ('VENDOR','Vendor',1,0),('CONTRACT','Contract',1,1);
INSERT INTO dbo.app_role(code, name) VALUES ('SYS_ADMIN','System Administrator'),('CS_OFFICER','Corporate Service Officer'),('APPROVER','Approver'),('COMPLIANCE','Compliance Officer'),('RISK_TEAM','Risk Team'),('UBA_STAFF','UBA Staff');
INSERT INTO dbo.app_user(username, full_name, email, password_hash) VALUES ('admin','Administrator','admin@uba.local','{noop}admin');
INSERT INTO dbo.user_role(user_id, role_id) SELECT u.user_id, r.role_id FROM dbo.app_user u, dbo.app_role r WHERE u.username='admin' AND r.code='SYS_ADMIN';
INSERT INTO dbo.service_category(name) VALUES ('Information Technology'),('Security'),('Cleaning & Maintenance'),('Consulting'),('Legal'),('Logistics'),('Marketing'),('Facilities'),('Other');
INSERT INTO dbo.document_type(entity_type, name) VALUES ('VENDOR','Company registration'),('VENDOR','Tax clearance'),('VENDOR','Business licence'),('VENDOR','Other'),('CONTRACT','Contract draft'),('CONTRACT','Signed copy'),('CONTRACT','Annex'),('CONTRACT','Other');
INSERT INTO dbo.approval_flow(entity_type, scope_id, name) VALUES ('CONTRACT', 0, 'Default contract flow');
INSERT INTO dbo.approval_flow_step(flow_id, step_no, name, approver_user_id) SELECT f.flow_id, s.n, s.nm, u.user_id FROM dbo.approval_flow f CROSS JOIN (SELECT 1 AS n, 'Legal Review' AS nm UNION ALL SELECT 2, 'Compliance Review' UNION ALL SELECT 3, 'Final Approval') s CROSS JOIN dbo.app_user u WHERE f.scope_id = 0 AND u.username = 'admin';
GO

-- audit_log so aceita INSERT (alem disso, recomenda-se DENY UPDATE, DELETE ao login da aplicacao)
CREATE TRIGGER trg_audit_log_immutable ON dbo.audit_log INSTEAD OF UPDATE, DELETE AS
BEGIN
    THROW 50001, 'audit_log is append-only', 1;
END;
GO
