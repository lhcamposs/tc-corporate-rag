# 🎓 Busca Semântica e RAG Corporativo (Protótipo de TCC)

Este repositório contém o protótipo de software desenvolvido para o Trabalho de Conclusão de Curso (TCC) intitulado **"Implementação de um Sistema de Busca Semântica e RAG para Bases de Conhecimento Corporativas"** no Instituto Federal Goiano - Campus Urutaí.

O objetivo do projeto é comparar uma abordagem de **Busca Semântica usando RAG (Retrieval-Augmented Generation)** com modelos de linguagem contra uma **baseline de Busca Lexical (SQL LIKE/BM25)** para extração de respostas em bases de conhecimento corporativas (documentos PDF e dados estruturados).

## 🛠️ Tecnologias Utilizadas
* **Java 25** + **Spring Boot 3**
* **Spring AI** (para orquestração de LLMs e Vetores)
* **PostgreSQL + pgvector** (Banco de dados relacional e vetorial)
* **Docker & Docker Compose** (Infraestrutura)
* **Ollama** (Modelos de LLM e Embeddings locais)

## 📋 Pré-requisitos

Antes de iniciar, certifique-se de ter as seguintes ferramentas instaladas em sua máquina:

1. **Java 25** (JDK configurado no `PATH`).
2. **Maven 3.8+** (Caso não queira usar o *wrapper* `./mvnw` incluso no projeto).
3. **Docker e Docker Compose** (Para subir o PostgreSQL com pgvector).
4. **Ollama** (Obrigatório caso esteja usando modelos locais como LLaMA 3.2 / nomic-embed-text para não depender de APIs pagas).

## ⚙️ Variáveis de Ambiente

O projeto é configurado nativamente pelo arquivo `application.properties`, mas você pode sobrescrever as variáveis de ambiente em produção ou para testes. As principais são:

| Variável | Descrição | Valor Padrão (Local) |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | URL de conexão do PostgreSQL | `jdbc:postgresql://localhost:5432/rag_db` |
| `SPRING_DATASOURCE_USERNAME`| Usuário do banco de dados | `postgres` |
| `SPRING_DATASOURCE_PASSWORD`| Senha do banco de dados | `postgres` |
| `SPRING_AI_OLLAMA_BASE_URL` | URL do servidor do Ollama (LLM/Embeddings)| `http://localhost:11434` |
| `SPRING_AI_OLLAMA_CHAT_MODEL`| Modelo de chat a ser utilizado | `llama3.2` |

> **Nota:** Certifique-se de baixar os modelos no seu Ollama local rodando no terminal: 
> `ollama run llama3.2` e `ollama pull nomic-embed-text`.

## 🚀 Passo a Passo: Subindo a Infraestrutura e Aplicação

1. **Clone o repositório:**
   ```bash
   git clone [https://github.com/lhcamposs/tc-corporate-rag.git](https://github.com/lhcamposs/tc-corporate-rag.git)
   cd tc-corporate-rag


2. **Inicie a infraestrutura de Banco de Dados:**
   O projeto conta com um arquivo `docker-compose.yaml` já configurado com a extensão `pgvector` e o script de inicialização `init-pgvector.sql`.
```bash
  docker-compose up -d

```


3. **Inicie a aplicação Spring Boot:**
   Utilize o Maven Wrapper já embutido no repositório.
```bash
# No Linux/Mac
./mvnw spring-boot:run

# No Windows
mvnw.cmd spring-boot:run

```


A aplicação iniciará na porta **8080**.

## 📚 Documentação e Swagger UI

Com a aplicação rodando, você pode acessar a interface interativa do Swagger para testar os endpoints sem necessidade do terminal:


👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

## 💻 Exemplos de Requisições (cURL)

Abaixo estão exemplos práticos de como interagir com as rotas principais da API.

### 1. Ingestão de Documento Corporativo (PDF)

Endpoint responsável por receber um PDF, ler o texto, dividir em chunks, gerar os embeddings e persistir tanto no banco relacional (para a baseline) quanto no vetorial (para o RAG).

```bash
curl --location 'http://localhost:8080/api/ingestion/pdf' \
--form 'file=@"/caminho/absoluto/para/o/seu/documento.pdf"'

```

### 2. Busca Semântica (Sistema RAG)

Realiza a consulta processada pela inteligência artificial. O sistema transforma a pergunta em um vetor, busca os trechos mais relevantes no PostgreSQL (pgvector) e aciona o LLM para formular uma resposta precisa.

```bash
curl --location 'http://localhost:8080/api/query/rag' \
--header 'Content-Type: application/json' \
--data '{
    "pergunta": "Sobre qual tipo de trabalho é esse documento?"
}'

```

### 3. Busca Lexical (Baseline - Comparação)

Realiza a consulta da mesma forma que os sistemas tradicionais (uso de SQL LIKE ou Full Text Search puro), servindo como baseline de avaliação para a métrica de comparação do TCC.

```bash
curl --location 'http://localhost:8080/api/query/baseline?termo=graficos'

```

---

**Autor:** Luan Henrique Campos Soares

**Orientador:** Dr. Junio Cesar de Lima

**Curso:** Sistemas de Informação - Instituto Federal Goiano (Campus Urutaí)

```

```