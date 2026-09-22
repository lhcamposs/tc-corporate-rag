-- Executado automaticamente pelo Postgres na primeira subida do container
-- (docker-entrypoint-initdb.d). Só roda quando o volume 'postgres_data'
-- é inicializado vazio — se você já subiu o container antes, rode este
-- arquivo manualmente (psql/pgAdmin) ou recrie o volume com
-- `docker compose down -v && docker compose up -d`.

CREATE
EXTENSION IF NOT EXISTS vector;

-- Tabela para busca lexical (baseline)
CREATE TABLE IF NOT EXISTS document_chunk (
                                              id SERIAL PRIMARY KEY,
                                              source_file TEXT,
                                              chunk_index INT,
                                              content TEXT
);

-- ---------------------------------------------------------------------
-- Base relacional de exemplo (objetivo específico (b) da proposta).
-- Simula uma base de conhecimento estruturada já existente na empresa
-- (ex.: artigos de wiki interna, FAQs, políticas) — fonte independente
-- dos PDFs, consultada e ingerida por RelationalIngestionService.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS artigo_conhecimento (
                                                   id SERIAL PRIMARY KEY,
                                                   category TEXT NOT NULL,
                                                   title TEXT NOT NULL,
                                                   content TEXT NOT NULL,
                                                   updated_at TIMESTAMP DEFAULT now()
    );

INSERT INTO artigo_conhecimento (category, title, content)
VALUES ('RH', 'Política de Férias',
        'Colaboradores têm direito a 30 dias corridos de férias por ano trabalhado, podendo ser fracionadas em até três períodos, sendo um deles de no mínimo 14 dias corridos. O período deve ser solicitado com pelo menos 30 dias de antecedência ao gestor direto.'),
       ('RH', 'Política de Home Office',
        'É permitido o regime híbrido de trabalho, com no mínimo 2 dias presenciais por semana definidos junto ao gestor da equipe. Equipamentos de trabalho remoto (notebook, monitor) são fornecidos pela empresa mediante termo de responsabilidade.'),
       ('TI', 'Procedimento de Reset de Senha',
        'Para redefinir a senha de acesso aos sistemas internos, o colaborador deve acessar o portal de autoatendimento e selecionar "Esqueci minha senha". Em caso de bloqueio da conta, abrir chamado com a equipe de TI através do sistema de tickets.'),
       ('TI', 'Política de Uso de VPN',
        'O acesso remoto à rede corporativa deve ser feito exclusivamente via VPN corporativa, com autenticação de dois fatores obrigatória. Conexões de redes públicas (aeroportos, cafés) exigem VPN ativa antes de qualquer acesso a sistemas internos.'),
       ('Financeiro', 'Processo de Reembolso de Despesas',
        'Despesas de viagem e representação devem ser lançadas no sistema de reembolso em até 15 dias corridos após a realização do gasto, acompanhadas de nota fiscal. Reembolsos acima de R$ 1.000,00 exigem aprovação adicional do gestor de centro de custo.'),
       ('Financeiro', 'Política de Cartão Corporativo',
        'O cartão corporativo é destinado exclusivamente a despesas relacionadas à atividade profissional. O uso indevido está sujeito às penalidades previstas no código de conduta da empresa e pode resultar em bloqueio do cartão.') ON CONFLICT DO NOTHING;

-- O Spring AI, ao inicializar o VectorStore (com initialize-schema: true no
-- application.properties), cria a tabela de embeddings automaticamente. Este
-- script só garante que a extensão pgvector e as tabelas-fonte já estão
-- disponíveis antes disso.