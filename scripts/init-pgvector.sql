-- Executado automaticamente pelo Postgres na primeira subida do container
-- (docker-entrypoint-initdb.d). Só roda quando o volume 'postgres_data'
-- é inicializado vazio — se você já subiu o container antes, rode este
-- arquivo manualmente (psql/pgAdmin) ou recrie o volume com
-- `docker compose down -v && docker compose up -d`.

CREATE
EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------
-- Busca lexical (baseline) com full-text search do PostgreSQL.
-- Bancos já existentes: aplique db/migrations/002_baseline_lexical_fts.sql.
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Configuração 'pt_unaccent': igual à 'portuguese', mas remove acentos antes do
-- stemming. Necessária porque unaccent() sozinha não é IMMUTABLE e não pode ser
-- usada em coluna gerada nem em índice.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'pt_unaccent') THEN
        CREATE TEXT SEARCH CONFIGURATION pt_unaccent (COPY = portuguese);
        ALTER TEXT SEARCH CONFIGURATION pt_unaccent
            ALTER MAPPING FOR hword, hword_part, word
            WITH unaccent, portuguese_stem;
END IF;
END
$$;

-- Tabela para busca lexical (baseline).
-- UNIQUE (source_file, chunk_index) torna a ingestão idempotente: reingerir o mesmo
-- arquivo atualiza as linhas (ON CONFLICT) em vez de duplicar.
-- Para bancos já existentes, aplique db/migrations/001_ingestao_idempotente.sql.
CREATE TABLE IF NOT EXISTS document_chunk (
                                              id SERIAL PRIMARY KEY,
                                              source_file TEXT NOT NULL,
                                              chunk_index INT NOT NULL,
    -- Mesmo ID do chunk no pgvector e no ChromaDB (rastreio por chunk).
    -- Bancos existentes: db/migrations/003_rastreio_por_chunk.sql.
                                              vector_id UUID NOT NULL,
                                              content TEXT NOT NULL,
                                              content_tsv tsvector
                                              GENERATED ALWAYS AS (to_tsvector('pt_unaccent', content)) STORED,
    CONSTRAINT uq_document_chunk_source_index UNIQUE (source_file, chunk_index),
    CONSTRAINT uq_document_chunk_vector_id UNIQUE (vector_id)
    );

CREATE INDEX IF NOT EXISTS idx_document_chunk_content_tsv
    ON document_chunk USING GIN (content_tsv);

-- ---------------------------------------------------------------------
-- Base relacional de exemplo (objetivo específico (b) da proposta).
-- Simula uma base de conhecimento estruturada (ex.: wiki interna, FAQs),
-- com registros redigidos a partir dos documentos públicos do IF Goiano do corpus —
-- fonte estruturada, consultada e ingerida por RelationalIngestionService.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS artigo_conhecimento (
                                                   id SERIAL PRIMARY KEY,
                                                   category TEXT NOT NULL,
                                                   title TEXT NOT NULL,
                                                   content TEXT NOT NULL,
                                                   updated_at TIMESTAMP DEFAULT now()
    );

INSERT INTO artigo_conhecimento (category, title, content)
VALUES ('Gestão de Pessoas', 'Férias do servidor',
        'O servidor técnico-administrativo tem direito a 30 dias de férias por ano, e o docente do Ensino Básico, Técnico e Tecnológico (EBTT), a 45 dias; professores temporários, substitutos e visitantes têm 30 dias. Para as primeiras férias são exigidos 12 meses de efetivo exercício. As férias podem ser usufruídas de forma integral ou parceladas em até três etapas, e a última etapa deve começar até 31 de dezembro. O servidor solicita as férias no SouGov.br (aplicativo ou web), e a chefia imediata as homologa no mesmo sistema. A antecipação ou o adiamento de férias já programadas deve ser pedido com pelo menos 45 dias de antecedência. Por necessidade do serviço justificada pela chefia, as férias podem ser acumuladas por no máximo dois períodos.'),
       ('Gestão de Pessoas', 'Auxílio-transporte',
        'O auxílio-transporte é um benefício indenizatório, pago antecipadamente, que custeia parte das despesas do servidor com transporte coletivo municipal, intermunicipal ou interestadual entre a residência e o local de trabalho. É devido quando a despesa mensal com transporte coletivo supera 6% do vencimento básico do cargo. Para solicitar ou atualizar o benefício, o servidor faz o pedido no SouGov.br e envia ao e-mail da Gestão de Pessoas da sua unidade a Declaração de Horário (Anexo I), que pode ser gerada no SUAP. A Gestão de Pessoas analisa o pedido e a compatibilidade entre os horários de trabalho e de transporte. O servidor deve manter o endereço atualizado e optar pelo meio de transporte mais econômico. As regras seguem a Instrução Normativa SRT/MGI nº 71/2025, em vigor desde 1º de março de 2025.'),
       ('Tecnologia da Informação', 'Requisitos de senha',
        'Pela Norma 03 do Comitê Gestor de Segurança da Informação (CGSIC), a senha de acesso aos sistemas do IF Goiano deve ter no mínimo oito caracteres e usar pelo menos três de quatro critérios: números, letras minúsculas, letras maiúsculas e caracteres especiais, sem regra de formação perceptível e sem repetir a última senha. A senha deve ser trocada a cada seis meses, no máximo, e imediatamente em caso de suspeita de violação. A senha é pessoal e intransferível, e quem a compartilha responde pelas ações de terceiros. Em caso de esquecimento, é fornecida uma senha temporária, que deve ser trocada no primeiro acesso; a senha anterior não pode ser recuperada. O setor de TI pode auditar a robustez das senhas, e o usuário com senha fraca tem até 24 horas para trocá-la, sob pena de bloqueio dos acessos.'),
       ('Tecnologia da Informação', 'E-mail institucional',
        'Têm direito ao e-mail institucional servidores técnico-administrativos, docentes, estagiários, professores substitutos ou temporários, pesquisadores, servidores em colaboração técnica, bolsistas e tutores. Para servidores e estagiários, a conta é criada automaticamente e o endereço, no padrão nome.sobrenome@ifgoiano.edu.br, é escolhido no primeiro acesso ao SUAP; os demais solicitam a conta por chamado. A senha do e-mail é definida pelo próprio usuário no SUAP. O uso do e-mail institucional é obrigatório para assuntos institucionais, e a caixa deve ser acompanhada diariamente. É proibido encaminhar mensagens do tipo corrente, divulgar propaganda e enviar comunicados de caráter associativo, sindical, religioso ou político-partidário. A conta do servidor é desativada automaticamente após a publicação do desligamento no Diário Oficial da União.'),
       ('Financeiro', 'Ajuda de custo',
        'A ajuda de custo é paga ao servidor que, no interesse da Administração, passa a ter exercício em nova sede com mudança de domicílio em caráter permanente, como nos casos de redistribuição, remoção de ofício, nomeação para cargo em comissão ou função de confiança e requisição, para compensar as despesas de viagem, mudança e instalação. O servidor abre processo no SUAP com o requerimento, o ato oficial do deslocamento, os comprovantes de residência na cidade de origem e de destino, o contracheque e os documentos dos dependentes. A Unidade de Gestão de Pessoas confere o processo e calcula o valor, e a Pró-Reitoria de Administração (PROAD) efetua o pagamento. O valor é calculado sobre a remuneração do mês do deslocamento, até o limite de três remunerações para quem tem três ou mais dependentes. O servidor deve devolver o valor se não se apresentar na nova sede em 30 dias sem justificativa ou se regressar, pedir exoneração ou abandonar o serviço antes de três meses.'),
       ('Financeiro', 'Cancelamento da execução financeira de PCDP no SCDP',
        'Para cancelar a execução financeira de uma Proposta de Concessão de Diárias e Passagens (PCDP) de exercícios anteriores, o usuário acessa o SCDP com a conta gov.br, altera o ano, abre a aba Execução > Cancelar Execução Financeira, pesquisa o número da PCDP, clica no nome do proposto, marca o documento hábil, informa o motivo do cancelamento e confirma. Em seguida, na aba Execução > Financeira, pesquisa a PCDP, preenche o centro de custos CC-GENÉRICO, mantém o código SIORG informado pelo sistema, informa o documento hábil e gera a AV. Depois, consulta a ordem bancária no SIAFI no ano do pagamento, registra a OB e confirma a execução financeira. Ao final, o SCDP encaminha a PCDP para prestação de contas.') ON CONFLICT DO NOTHING;

-- O Spring AI, ao inicializar o VectorStore (com initialize-schema: true no
-- application.properties), cria a tabela de embeddings automaticamente. Este
-- script só garante que a extensão pgvector e as tabelas-fonte já estão
-- disponíveis antes disso.
