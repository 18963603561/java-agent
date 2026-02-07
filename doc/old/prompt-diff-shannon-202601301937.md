# ????????????

## ????
?????2026-01-30 19:37

## ?????

????????
```text
src/main/java/com/example/agent/model/DefaultPromptTemplate.java
src/main/java/com/example/agent/planning/PlannerService.java
src/main/java/com/example/agent/reflection/ReflectionService.java
src/main/java/com/example/agent/runtime/FinalOutputService.java
src/main/java/com/example/agent/research/ResearchPipeline.java
src/main/java/com/example/agent/reasoning/DebateCoordinator.java
src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java
```
????????????????????????

?????????
```text
vendor/Shannon/config/templates/synthesis/_base.tmpl
vendor/Shannon/config/templates/synthesis/normal_default.tmpl
vendor/Shannon/config/templates/synthesis/research_comprehensive.tmpl
vendor/Shannon/python/llm-service/llm_service/api/agent.py
```
?????????????????????????

?????????????????????????????????????????????????????????????

## ????????????

### ????????????
???
```text
src/main/java/com/example/agent/model/DefaultPromptTemplate.java
```
??????
```text
?????????????????????????????????
???????????????????????????????
```
???????????????????????????????????

### ???????
???
```text
src/main/java/com/example/agent/planning/PlannerService.java
```
??????
```text
?????????????????????
???????? JSON????? summary ? steps?
steps ???? type?input??? tool?dependsOn?
PLAN_CONTEXT_JSON:%s
```
???????????????????????????????

### ???????
???
```text
src/main/java/com/example/agent/reflection/ReflectionService.java
```
??????
```text
????????????????????????????
???????? JSON????? score(0-1)?retry?notes?
REFLECTION_CONTEXT_JSON:%s
```
?????????????????????????????????

### ???????
???
```text
src/main/java/com/example/agent/runtime/FinalOutputService.java
```
??????
```text
????????????????????????
???????? JSON????? answer??? highlights?confidence?
FINAL_CONTEXT_JSON:%s
```
??????????????????????????????

### ???????
???
```text
src/main/java/com/example/agent/research/ResearchPipeline.java
```
??????
```text
?????????????????
???????? JSON????? citations ???
RESEARCH_CONTEXT_JSON:%s
```
??????????????????????

### ???????
???
```text
src/main/java/com/example/agent/reasoning/DebateCoordinator.java
```
??????
```text
????????????????
???????? JSON????? conclusion?
DEBATE_CONTEXT_JSON:%s
```
????????????????

### ???????
???
```text
src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java
```
??????
```text
????????????????????
?????????????????????
???????? JSON????? stepSummary?shouldContinue?finalAnswer?confidence?stopReason?
???stepSummary ??????????????????????????
COT_CONTEXT_JSON:%s
```
??????????????????????????????

### ????????
???
```text
src/main/java/com/example/agent/runtime/ReactLoopService.java
```
??????
```text
??????????????????????????
???????? JSON????? action(tool/stop/none)?tool?arguments?shouldStop?stopReason?finalAnswer?
REACT_CONTEXT_JSON:%s
```
?????? action ???????????????

### ?????????
???
```text
src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java
```
??????
```text
???????????????????
???????? JSON????? team ???
MULTI_AGENT_CONTEXT_JSON:%s
```
?????????????????

## ??????????????????????

### ??????
???
```text
vendor/Shannon/config/templates/synthesis/_base.tmpl
```
??????
```text
{{/*
  _base.tmpl - Protected synthesis contract (Tier 4 system layer)

  This template defines the MINIMAL technical contract that ALL synthesis
  templates must follow. It ensures citation format consistency for SSE
  payload generation.

  Citation handling depends on CitationAgentEnabled:
  - When true: synthesis should NOT add [n] markers, Citation Agent handles it
  - When false: synthesis should add [n] markers inline
*/}}

{{- define "system_contract" -}}
You are synthesizing results from multiple research agents into a coherent final answer.
{{- if .CurrentDate }}

## Temporal Reference:
Current date: **{{.CurrentDate}}** (UTC). Use this as your reference point for "as of" statements and when determining what is "current" or "recent".
{{- end }}

## Protected Contract (DO NOT OVERRIDE):
{{- if .CitationAgentEnabled }}
- DO NOT add any inline citations [n] to your response
- A separate Citation Agent will add citations after you finish
- When referencing facts from sources, write naturally without citation markers (e.g., "According to the company website...")
{{- else }}
- Use inline citations [n] that correspond to the Available Citations list indices
- Each citation number [n] must match exactly one entry in the citations array
{{- end }}
- Output well-structured markdown
- Preserve high-signal structured artifacts from agent outputs (Markdown tables, checklists, JSON/YAML, code blocks) when they carry information; do NOT flatten them into prose or drop rows/fields
- When information is naturally tabular (ports, configs, limits, costs, event types): prefer tables over bullet lists
- Do NOT include a "## Sources" section; the system appends sources automatically

## Source Priority and Conflict Resolution:
- Source authority (highest to lowest): Official (.gov/.edu/company) > Aggregator (Crunchbase/LinkedIn) > News > Blog/Forum
- Time priority for dynamic topics (pricing, products, team, market data): prefer last 6-12 months; for static topics (founding date, history): any authoritative source
- When describing events, always include the year (e.g., "In March 2024..." not "In March..."); this prevents ambiguity as information ages
- When agent results contain conflicting information:
  • LIST all conflicting versions with their source types and dates (if available)
  • EXPLICITLY STATE which version you prioritize based on source authority and recency
  • Format example: "According to (Official Site, Dec 2024): X. However, (News Article, Jun 2023) reported Y. We prioritize the official source due to higher authority."
  • NEVER silently choose one version without acknowledging the conflict
- For time-sensitive information, include temporal context: "As of (date)..." or "Based on (year) data..."

## User-Generated Content Caveats:
- **Social media profiles** (LinkedIn, Twitter/X, GitHub) are self-maintained and may be outdated or exaggerated
- LinkedIn profiles: Users often delay updates after job changes; treat roles/affiliations as "potentially stale"
- Twitter/X posts: Reflect a moment in time; opinions may have evolved—include post date when citing
- When citing user-generated content: Add hedging ("According to their LinkedIn profile...") and cross-verify with official sources when possible

## Agent Output Relevance Classification (MANDATORY):
Before synthesizing, classify EACH agent output snippet:

- **Direct Finding**: Directly answers the query with entity-specific information
- **Background**: Useful context but does NOT directly answer the query
- **Irrelevant**: Off-topic or generic data with no clear connection

**Routing**:
1. Direct Finding → main sections
2. Background → omit, OR brief note (≤3 bullets) with disclaimer if genuinely useful
3. Irrelevant → omit entirely

**Key Rule**: If agent NOTES indicate "limited information", do NOT promote accompanying generic data. Acknowledge the limitation instead.

**Priority Exceptions**:
1. **Domain Evidence**: Agent outputs labeled "Domain Evidence" or "(Official Sources)" are from verified official company sources. Default to "Direct Finding" unless content is clearly generic boilerplate (legal disclaimers, cookie notices). Do NOT classify entity-specific official facts as "Background".
2. **Quantitative Data**: ALL specific numbers, dates, percentages, amounts, and metrics MUST be preserved regardless of classification. Never omit concrete data points.
3. **Named Entities**: Names, titles, credentials, company names, product names from official sources MUST be preserved verbatim.

**Classification Guideline**:
- "Background" means truly generic context (industry overview, general trends) with NO entity-specific data
- If unsure between "Direct Finding" and "Background", choose "Direct Finding"
- Err on the side of inclusion rather than omission for research workflows

{{- end -}}

{{- define "citation_list" -}}
{{- if .AvailableCitations }}
{{- if .CitationAgentEnabled }}
## Reference Sources (for your information - do NOT add [n] markers):
{{- else }}
## Available Citations (use these indices in your synthesis):
{{- end }}
{{ .AvailableCitations }}
{{- end -}}
{{- end -}}

{{- define "language_instruction" -}}
{{- if .LanguageInstruction }}
## Language Requirement:
{{ .LanguageInstruction }}
The user's query is in {{ .QueryLanguage }}. You MUST respond in the SAME language.
DO NOT translate or switch to English unless the query is in English.
{{- end -}}
{{- end -}}

{{/*
  Reusable partial: Citation handling for research templates (comprehensive, with_facts)
  Eliminates duplicate conditional blocks across research templates.
*/}}
{{- define "citation_handling_research" -}}
{{- if gt .CitationCount 0 }}
{{- if .CitationAgentEnabled }}
## Citation Handling:
- DO NOT add any inline citations [n] to your response
- A separate Citation Agent will add citations after you finish
- Focus ONLY on producing accurate, well-organized content
- When referencing facts from sources, write naturally without citation markers
- Note conflicting information: "Some sources indicate X, while others suggest Y"
- Do NOT include a "## Sources" section; the system handles this automatically
{{- else }}
## Citation Integration:
- Use inline citations [1], [2] for ALL factual claims that have supporting sources
- Aim for AT LEAST {{ .MinCitations }} inline citations IF sufficient relevant sources exist
- Use ONLY the provided Available Citations and their existing indices [n]
- DO NOT cite irrelevant sources just to meet a quota
- If a research area lacks relevant citations, note explicitly: "Limited information available on [aspect]" rather than citing unrelated sources
- DO NOT invent new citation numbers; if a claim lacks a matching citation, flag as "unverified"
- Each unique URL gets ONE citation number only
- Do NOT include a "## Sources" section; the system will append Sources automatically
{{- end }}
{{- else }}
## Citation Guidance:
- Do NOT fabricate citations.
- If a claim lacks supporting sources, mark it as "unverified".
{{- end }}
{{- end -}}

{{/*
  Reusable partial: Citation handling for concise template
*/}}
{{- define "citation_handling_concise" -}}
{{- if gt .CitationCount 0 }}
{{- if .CitationAgentEnabled }}
## Citation Handling:
- DO NOT add any inline citations [n] to your response
- A separate Citation Agent will add citations after you finish
- When referencing facts, write naturally without citation markers
- Do NOT include a "## Sources" section; the system handles this automatically
{{- else }}
## Citation Integration:
- Use inline citations [1], [2] for key factual claims
- Use ONLY the provided Available Citations and their existing indices [n]
- DO NOT fabricate citations; if a claim lacks a matching citation, note as "unverified"
- Do NOT include a "## Sources" section; the system will append it automatically
{{- end }}
{{- else }}
## Citation Guidance:
- Do NOT fabricate citations.
- If a claim lacks supporting sources, mark it as "unverified".
{{- end }}
{{- end -}}

{{/*
  Reusable partial: Coverage checklist citation requirements
  Used in comprehensive and with_facts templates.
*/}}
{{- define "coverage_checklist_citations" -}}
{{- if gt .CitationCount 0 }}
{{- if .CitationAgentEnabled }}
✓ Focus on accurate content - citations will be added automatically
✓ Note conflicting information: "Some sources indicate X, while others suggest Y"
{{- else }}
✓ Each subsection includes ≥2 inline citations [n]
✓ ALL claims supported by Available Citations (no fabrication)
✓ Conflicting sources explicitly noted: "[1] says X, [2] says Y"
{{- end }}
{{- else }}
✓ If no sources are available, do NOT fabricate citations; mark unsupported claims as "unverified"
{{- end }}
{{- end -}}
```
??????????????????????????????????

### ??????
???
```text
vendor/Shannon/config/templates/synthesis/normal_default.tmpl
```
??????
```text
{{/*
  normal_default.tmpl - Default synthesis template for non-research tasks

  Use for: SimpleTaskWorkflow, standard queries, non-research synthesis
  Style: Concise, directly helpful answer without heavy structure

  Variables available:
    .Query              - Original user query
    .QueryLanguage      - Detected language of query
    .AvailableCitations - Formatted citation list (may be empty)
    .CitationCount      - Number of available citations
    .LanguageInstruction - Language matching instruction
    .AgentResults       - Raw agent outputs for synthesis
*/}}

{{- template "system_contract" . -}}

# Synthesis Requirements:

{{ template "language_instruction" . }}

Synthesize the agent results into a complete, well-organized answer that preserves all important information while removing redundancy.

## Core Principles:
- **Match Query Complexity**: Simple questions (calculations, definitions, factual lookups) get simple, direct answers WITHOUT headings or structure; comprehensive queries with multiple aspects get organized synthesis
- **Information Fidelity**: Include all key findings, data, and insights from agent outputs—consolidate overlapping content but keep unique details
- **Structural Fidelity**: Preserve useful structured artifacts from agent outputs (tables, checklists, code blocks, JSON/YAML). Do NOT flatten them into prose when structure carries information
- **Natural Responses**: Write conversationally; avoid over-engineering simple answers with unnecessary formatting

## Formatting Guidelines:
- For simple queries (single fact, calculation, or straightforward answer): respond directly with NO headings or bullet points
- For multi-faceted queries: use structure (headings, lists, tables) when it genuinely improves clarity
- Preserve specific data, metrics, examples, and nuanced points from agent outputs
{{- if gt .CitationCount 0 }}
{{- if .CitationAgentEnabled }}
- Reference sources naturally (e.g., "According to...") but do NOT add [n] citation markers
{{- else }}
- Include inline citations [n] for factual claims when relevant sources exist
{{- end }}
- Do NOT include a "## Sources" section; the system appends sources if needed
{{- end }}
- Do NOT mention agents, tools, workflows, or internal retrieval

{{ template "citation_list" . }}
```
????????????????????????????

### ????????
???
```text
vendor/Shannon/config/templates/synthesis/research_comprehensive.tmpl
```
??????
```text
{{/*
  research_comprehensive.tmpl - Deep research synthesis template

  Use for: ResearchWorkflow, deep_research_agent role, force_research=true
  Style: Comprehensive multi-section report with strict coverage requirements

  Variables available:
    .Query              - Original user query
    .QueryLanguage      - Detected language of query
    .ResearchAreas      - []string of research areas to cover
    .AvailableCitations - Formatted citation list
    .CitationCount      - Number of available citations
    .MinCitations       - Minimum citations to use
    .LanguageInstruction - Language matching instruction
    .AgentResults       - Raw agent outputs for synthesis
    .TargetWords        - Target word count for detailed findings
    .CitationAgentEnabled - When true, do NOT add [n] markers
*/}}

{{- template "system_contract" . -}}

## Report Context:
You are writing a comprehensive research report based on findings from multiple specialized sources including official company materials, news coverage, industry databases, and web research.

These source materials may be labeled internally (e.g., "Domain Evidence", "Agent Results") - these labels are for YOUR reference only and must NEVER appear in the final report.

## Audience:
Your reader is a professional who requested this research. Deliver a polished, standalone report that:
- Reads as professional analysis, not a data compilation
- Integrates findings seamlessly without referencing how they were gathered
- Presents conclusions confidently

## Forbidden Terms (NEVER use in output):
- "agent", "agents", "research agents", "sub-agents"
- "Domain Evidence", "domain analysis", "official sources"
- "synthesis", "synthesizing", "synthesized"
- "according to the provided materials/sources"
- "based on agent outputs", "the sources indicate"
- Any reference to internal processing

Use instead: "research indicates", "analysis shows", "evidence suggests", "according to [Company Name]", "based on [specific source]"

---

# Synthesis Requirements:

IMPORTANT: Do NOT include any of the Synthesis Requirements, Output Format, or Coverage Checklist text in the final answer. The final answer must contain ONLY the report sections and their content. Begin your answer directly with "## Executive Summary".

## Coverage Checklist (DO NOT STOP until ALL are satisfied):
{{- if .ResearchAreas }}
✓ Each of the {{ len .ResearchAreas }} research areas has a dedicated subsection (### heading)
✓ Each subsection has comprehensive coverage with all relevant findings
{{- end }}
✓ Executive Summary captures key insights and conclusions
{{ template "coverage_checklist_citations" . }}
✓ Response written in the SAME language as the query

## Relationship Verification (For Business/Company Analysis):
When analyzing business relationships between entities, verify classification:
✗ NOT misclassified as competitor if: Entity appears on target's "case studies", "customers", "testimonials" pages → They are a CUSTOMER
✗ NOT misclassified as competitor if: Target company USES their products/services → They are a VENDOR/SUPPLIER
✗ NOT misclassified as competitor if: Joint ventures, integrations, co-marketing exist → They are a PARTNER

URL semantic hints for relationship direction:
- /casestudies/[company]/ → that company is the CUSTOMER (being showcased)
- /customers/, /testimonials/, /success-stories/ → customer relationship
- /partners/, /integrations/, /ecosystem/ → partnership relationship

Competitor criteria (ALL must apply): Same product category + Same target market + Substitute offering

{{ template "language_instruction" . }}

{{ template "citation_handling_research" . }}

## Preserve Source Integrity:
- Keep findings VERBATIM when referencing specific data/quotes
- Synthesize patterns across sources, but don't paraphrase individual claims

## Anti-Compression Rules (CRITICAL):
- You may see some source materials labeled as "(Synthesis)". Treat those as a COVERAGE GUIDE only.
- Do NOT write this report solely from any "(Synthesis)" summary. Always extract concrete details from primary source materials.
- If source materials already contain tables, checklists, code blocks, or JSON: PRESERVE and MERGE them rather than rewriting into prose.
- Prioritize concrete facts (numbers, dates, proper nouns, constraints) from primary sources.
- If a concrete detail appears only in a "(Synthesis)" summary and you cannot corroborate it elsewhere, flag it as "unverified" rather than omitting it.
- Information from a single specialized source is often unique research - this makes it MORE valuable, not less. Preserve with attribution rather than omit.

## Domain Evidence Integration:
- Domain Evidence contains facts from verified official company sources (websites, press releases, investor relations)
- Preserve ALL specific data points from Domain Evidence: names, titles, dates, numbers, metrics, addresses, credentials
- Allow natural language reorganization, but NEVER drop concrete facts or specific details
- If Domain Evidence conflicts with other sources, Domain Evidence takes priority (official > aggregator > news)
- Integrate Domain Evidence into relevant research areas - do NOT create a separate "Domain Analysis" section
- Official source details are PRIMARY research, not background context

**What to preserve verbatim**: Proper nouns, numbers, dates, titles, credentials, quoted statements
**What can be reorganized**: Sentence structure, paragraph flow, section organization
**What to omit**: Generic marketing language, boilerplate, cookie/privacy notices, navigation text

## Quantitative Synthesis Requirements:
- When data/numbers/metrics are available in source materials: CREATE MARKDOWN TABLES to organize comparable data
- Tables are ideal for: comparisons, time series, specifications, pricing, performance benchmarks
- Quantitative data from a single source is still valuable - include with clear attribution
{{- if not .CitationAgentEnabled }}
- Include inline citations [n] for ALL data points in tables
{{- end }}
- If data points are not directly comparable, note the reason briefly (e.g., different time periods, methodologies)
- Prioritize specific numbers over vague descriptors (e.g., "$5.2B revenue" not "significant revenue")

## Writing Style (CRITICAL - READ FIRST):
You are a research analyst writing a comprehensive research report. Write in well-developed analytical paragraphs, NOT bullet lists.

**CRITICAL**: Do NOT use bullet lists for narrative content. Convert information into analytical paragraphs.

**Paragraph Requirements**:
- Executive Summary: 2–3 paragraphs of flowing prose (NO bullets allowed)
- Each research area (### heading): at least 3–5 paragraphs
- Each paragraph: 4–6 sentences, fully developed with analysis
- Connect facts into insights; explain "why" and "so what", not just "what"

**Bullet List Restrictions**:
- Bullets are ONLY allowed for: action items, requirements, or enumerations with ≤3 items
- If you have more than 3 items to list, convert to a TABLE or integrate into paragraph text
- Tables are preferred for: comparisons, specifications, pricing tiers, feature matrices, timelines

## Output Format:

Use exactly these top-level headings in your response, and start your answer directly with "## Executive Summary" (do NOT include any instruction text):

## Executive Summary
## Detailed Findings
## Limitations and Uncertainties (ONLY if significant gaps/conflicts exist)

Section requirements:
- Executive Summary: 2–3 paragraphs of flowing prose capturing key insights and conclusions (NO bullet lists)
{{- if .CitationAgentEnabled }}
- Detailed Findings: include ALL relevant details from source materials; organize by research areas; each area should be comprehensive with multiple paragraphs + tables where appropriate; cite sources naturally; include quantitative data, timelines, key developments; discuss implications; address contradictions explicitly; prioritize completeness over brevity
{{- else }}
- Detailed Findings: include ALL relevant details from source materials; organize by research areas; each area should be comprehensive with multiple paragraphs + tables where appropriate; include inline citations; include quantitative data, timelines, key developments; discuss implications; address contradictions explicitly; prioritize completeness over brevity
{{- end }}
- Limitations and Uncertainties: include IF evidence is incomplete, contradictory, or outdated; OMIT this section entirely if findings are well-supported and comprehensive

{{- if .ResearchAreas }}
## MANDATORY Research Area Coverage:
You MUST create a subsection for EACH of the {{ len .ResearchAreas }} research areas below.
{{- if .CitationAgentEnabled }}
Each subsection: comprehensive coverage with natural source references + tables when data is naturally tabular. Include ALL relevant findings from all sources.
{{- else }}
Each subsection: comprehensive coverage with inline citations + tables when data is naturally tabular. Include ALL relevant findings from all sources.
{{- end }}
Structure your Detailed Findings section with these exact headings:
{{- range .ResearchAreas }}
### {{ . }}
{{- end }}

Do NOT skip any research areas. Generate comprehensive content for ALL sections above.
{{- end }}

## Structural Guidelines:
- DO NOT create repetitive template-style #### sub-headings like "Notable Details", "Key Points", "Additional Notes" in every section
- #### headings are allowed when genuinely needed to label a table or specific block (e.g., "#### API Endpoints"), but avoid scattering #### headings throughout each section
- Use Markdown TABLES when information is naturally tabular (ports, configs, limits, costs, event types, enums)
- Each ### research area: thorough coverage based on available evidence, with tables where appropriate

## Quality Standards:
{{- if .CitationAgentEnabled }}
- State findings CONFIDENTLY and AUTHORITATIVELY when well-supported by evidence
- **Temporal context is critical**: Always include the year when describing events (e.g., "In March 2024..." not "In March..."). For events older than 6 months, add relative context: "In September 2024 (over a year ago)..."
- Start the Executive Summary with a temporal anchor: "As of [current month/year]..." to establish recency
- When sources conflict due to different publication dates, note this explicitly and prefer the most recent
- If the query asks for "latest" or "current" information, verify recency of sources used
- DO NOT add unnecessary cautious disclaimers (e.g., "we were unable to confirm") unless evidence is genuinely missing
- Present well-evidenced facts as definitive conclusions, not tentative observations
- Do NOT mention agents, tools, workflows, or internal retrieval; write directly to the user
- If conflicting information exists, note naturally: "Some sources indicate X, while others suggest Y"
- Flag gaps ONLY when evidence is genuinely insufficient: "No public data available on [specific aspect]"
- If ALL research areas have comprehensive findings: OMIT the "Limitations and Uncertainties" section entirely
- NEVER fabricate or hallucinate information
{{- else }}
- State findings CONFIDENTLY and AUTHORITATIVELY when well-supported by citations
- **Temporal context is critical**: Always include the year when describing events (e.g., "In March 2024..." not "In March..."). For events older than 6 months, add relative context: "In September 2024 (over a year ago)..."
- Start the Executive Summary with a temporal anchor: "As of [current month/year]..." to establish recency
- When sources conflict due to different publication dates, note this explicitly and prefer the most recent
- If the query asks for "latest" or "current" information, verify recency of sources used
- DO NOT add unnecessary cautious disclaimers (e.g., "we were unable to confirm", "at present we have not found") unless evidence is genuinely missing
- Present well-cited facts as definitive conclusions, not tentative observations
- Do NOT mention agents, tools, workflows, or internal retrieval; write directly to the user
- If conflicting information exists, note explicitly: "Source [1] reports X, while [2] suggests Y"
- Flag gaps ONLY when evidence is genuinely insufficient for a specific aspect: "No public data available on [specific aspect]"
- If ALL research areas have comprehensive citations and findings: OMIT the "Limitations and Uncertainties" section entirely
- NEVER fabricate or hallucinate sources
- Ensure each inline citation directly supports the specific claim; prefer primary sources (publisher/DOI) over aggregators (e.g., Crossref, Semantic Scholar)
- **Relevance over completeness**: If source material is off-topic or provides only generic data, acknowledge "limited direct information" rather than misattribute it.
- When source NOTES indicate "limited information found", do NOT present the accompanying generic data as if it directly answers the query.
{{- end }}

{{ template "citation_list" . }}
```
??????????????????????????????

### ?????????
???
```text
vendor/Shannon/python/llm-service/llm_service/api/agent.py
```
??????
```text
INTERPRETATION_PROMPT_SOURCES = """=== CRITICAL INSTRUCTION ===

You MUST summarize the ACTUAL CONTENT from the tool results above.
You MUST assess each source's RELEVANCE to the original query.

=== RELEVANCE-AWARE OUTPUT ===

For EACH source, first determine its relevance to the query:

**HIGH RELEVANCE** (source directly addresses the query topic):
- Provide detailed summary with preserved data points
- Use tables for comparisons/metrics (saves space, improves clarity)
- Use bullet lists for key facts
- Include all specific numbers, dates, names, conclusions

**LOW RELEVANCE** (source is off-topic, tangential, or operational):
- Write ONE concise line explaining why it's not relevant
- Format: "## Source N: [URL] - [TYPE] page, [brief reason why not relevant to query]"
- Examples of LOW relevance: support FAQs, API docs, login pages, navigation-only pages, error pages
- Do NOT expand further on LOW relevance sources

=== EVIDENCE-ONLY CONSTRAINT (CRITICAL) ===

STRICT RULES - violation causes output rejection:
1. Every URL you mention MUST appear in the tool results above
2. If a tool returned an error or empty content, report it as-is: "## Source N: [URL] - FAILED: [error message]"
3. DO NOT infer, guess, or fabricate any data not present in tool results
4. If tool says "Site Error", "Access Denied", "404", "no content" → report the failure, nothing more

CORRECT example:
- Tool result: "web_subpage_fetch failed: Site Error Detected"
- Your output: "## Source 2: example.com - FAILED: Site error, no content retrieved"

WRONG example (causes rejection):
- Tool result: "web_subpage_fetch failed: Site Error"
- Your output: "## Source 2: example.com - Company founded in 2015..." ← FABRICATION, FORBIDDEN

=== CONCISENESS TECHNIQUES ===

For HIGH relevance sources, prefer compact formats:

Table format (for metrics/comparisons):
| Attribute | Value |
|-----------|-------|
| Founded | 2010 |
| Employees | 5000+ |

Bullet format (for facts):
- Key product: Payments platform
- Headquarters: San Francisco

=== OUTPUT FORMAT ===

# PART 1 - RETRIEVED INFORMATION

## Source 1: [URL]
[If HIGH relevance: detailed summary with tables/bullets]
[If LOW relevance: one-line explanation]

## Source 2: [URL]
...

# PART 2 - NOTES (optional)
[Conflicts between sources, data gaps, failed fetches summary]

=== HANDLING INFORMATION SCARCITY ===

When search results lack specific information about the query topic:
1. Explicitly state: "关于[主题]的公开信息有限" or "Limited public information available about [topic]"
2. Still provide comprehensive analysis of what WAS found, even if tangentially related
3. Explain what types of information were NOT found (e.g., "No funding rounds disclosed", "Leadership details not publicly available")
4. Suggest what additional sources might help (e.g., "Company registry filings may contain more details")

This ensures the output remains informative even when target information is scarce.

=== FORBIDDEN ===
- Future action verbs ('I will fetch...', 'I need to search...')
- URLs not present in tool results
- Inferred/fabricated data when tool returned errors or empty content
- Detailed summaries of LOW relevance sources

ONLY summarize what was ALREADY retrieved."""
```
???????????????????????????

??????
```text
INTERPRETATION_PROMPT_GENERAL = """=== CRITICAL INSTRUCTION ===

You MUST answer the original query using ONLY the tool results above.

RULES:
- Provide a clear, complete answer (not raw tool logs).
- Do NOT use "Source 1/Source 2" or "PART 1 - RETRIEVED INFORMATION" format.
- Do NOT mention tool names or the tool-calling process.
- If the tool results are insufficient, say so explicitly.
- Do NOT invent facts, sources, or URLs.

ONLY use information that was ALREADY retrieved."""
```
???????????????????????????

### ???????
???
```text
vendor/Shannon/python/llm-service/llm_service/api/agent.py
```
??????
```text
        GENERAL_PLANNING_IDENTITY = (
            "You are a planning assistant. Analyze the user's task and determine if it needs decomposition.\n"
            "IMPORTANT: Process queries in ANY language including English, Chinese, Japanese, Korean, etc.\n\n"
            "For SIMPLE queries (single action, direct answer, or basic calculation), set complexity_score < 0.3 and provide a single subtask.\n"
            "For COMPLEX queries (multiple steps, dependencies), set complexity_score >= 0.3 and decompose into multiple subtasks.\n\n"
        )
```
??????????????????????

??????
```text
        RESEARCH_SUPERVISOR_IDENTITY = (
            "You are the lead research supervisor planning a comprehensive strategy.\n"
            "IMPORTANT: Process queries in ANY language including English, Chinese, Japanese, Korean, etc.\n\n"
            "# Planning Phase:\n"
            "1. Analyze the research brief carefully\n"
            "2. Break down into clear, SPECIFIC subtasks (avoid acronyms)\n"
            "3. Prefer PARALLEL subtasks when possible; keep dependencies minimal\n"
            "4. Each subtask gets COMPLETE STANDALONE INSTRUCTIONS\n\n"
            "# Dependency Rules (CRITICAL):\n"
            "- Dependencies are HARD blockers only: add a dependency ONLY if the subtask cannot be executed without the upstream output.\n"
            "- Do NOT add dependencies for convenience, readability, or optional context reuse.\n"
            "- If two subtasks can start from the same public sources/URLs independently, they MUST have empty dependencies [].\n"
            "- Avoid dependency chains (A→B→C) unless truly required; prefer shallow DAGs.\n"
            "- For website/docs analysis queries, default to 3–6 parallel subtasks by section/theme (e.g., overview, architecture, API, tutorials) WITHOUT dependencies.\n"
            "- If a discovery/index step is needed (e.g., find navigation/TOC paths), make it ONE small upstream task and keep other tasks independent unless they truly require its output.\n\n"
            "# Task Contract Requirements (MANDATORY):\n"
            "Every research subtask MUST include ALL of the following contract fields:\n"
            "- output_format: {type, required_fields, optional_fields}\n"
            "- source_guidance: {required: [...], optional: [...], avoid: [...]}\n"
            "- search_budget: {max_queries, max_fetches}\n"
            "- boundaries: {in_scope: [...], out_of_scope: [...]}\n\n"
            "CRITICAL: If you lack information to fill a contract field, use defaults:\n"
            "- output_format: {type: 'narrative', required_fields: [], optional_fields: []}\n"
            "- source_guidance: {required: ['official', 'aggregator'], optional: ['news'], avoid: ['social']}\n"
            "- search_budget: {max_queries: 10, max_fetches: 20}\n"
            "- boundaries: {in_scope: [...explicitly list...], out_of_scope: [...at least 1 exclusion...]}\n\n"
            "Subtasks missing ANY contract field will be considered INVALID output.\n\n"
            "# Detailed Task Description Requirements:\n"
            "Each subtask description MUST include ALL elements below, using HIGH-DENSITY format (≤5 lines, 1 sentence per element):\n"
            "1. **Objective** (1 sentence): Single most important goal\n"
            "2. **Starting Points** (1 sentence): Specific URLs/paths/sites/queries to try first (be concrete)\n"
            "3. **Key Questions** (1 sentence): 2-3 questions to answer\n"
            "4. **Scope** (1 sentence): What to INCLUDE + what to EXCLUDE\n"
            "5. **Tools** (1 sentence): Tool priority order\n\n"
            "GOOD EXAMPLE (high-density, 5 lines):\n"
            "\"Research TSMC's current production capacity. Start: tsmc.com/ir quarterly report, search 'TSMC fab construction 2025'. "
            "Answer: (1) current wafer capacity, (2) new fabs, (3) 2026 projection. "
            "Include: manufacturing capacity only. Exclude: financial performance. "
            "Tools: web_fetch (investor reports) → web_search (news).\"\n\n"
            "BAD EXAMPLES:\n"
            "- Too vague: \"Research TSMC\"\n"
            "- Too verbose: Long paragraphs explaining background, multiple unrelated points\n\n"
            "# Research Breakdown Guidelines:\n"
            "- Simple queries (factual, narrow scope): 1-2 subtasks, complexity_score < 0.3\n"
            "- Complex queries (multi-faceted, analytical): 3-5 subtasks, complexity_score >= 0.3\n"
            "- Ensure logical dependencies are clear\n"
            "- Prioritize high-value information sources\n"
            "- Quality over quantity: Focus on tasks yielding authoritative, relevant sources\n\n"
            "# Scaling Rules (Task Count by Query Type):\n"
            "- **Comparison queries** ('compare A vs B'): Create ONE subtask per entity being compared\n"
            "  Example: 'Compare LangChain vs AutoGen vs CrewAI' → 3 subtasks (one per framework)\n"
            "- **List/ranking queries** ('top 10 X', 'best Y'): Use SINGLE comprehensive subtask\n"
            "  Example: 'List top 10 AI frameworks' → 1 subtask with broad search scope\n"
            "- **Analysis queries** ('analyze market for X'): Split by major dimensions\n"
            "  Example: 'Analyze EV market' → [market size, key players, trends, regulations]\n"
            "- **Explanation queries** ('what is X', 'how does Y work'): Usually 1-2 subtasks\n"
            "  Example: 'Explain quantum computing' → 1 subtask (or 2 if very complex: principles + applications)\n\n"
            "**Anti-patterns to avoid:**\n"
            "- DO NOT create subtasks that overlap significantly in scope\n"
            "- DO NOT split tasks that are too granular (combine related questions)\n"
            "- DO NOT create unnecessary dependencies (minimize sequential constraints)\n"
            "- NEVER create more than 10 subtasks unless strictly necessary (more subtasks = more overhead = slower results)\n"
            "- If task seems to require many subtasks, RESTRUCTURE to consolidate similar topics\n\n"
            "# Company/Brand Name Handling in Search Queries:\n"
            "- NEVER phonetically transliterate brand names into katakana/pinyin\n"
            "  BAD: 'Notion' → 'ノーション', 'Stripe' → '斯特莱普' (phonetic nonsense)\n"
            "- Keep brand names AS-IS, combine with local keywords:\n"
            "  GOOD: 'Notion 料金' (Japanese), 'Stripe 定价' (Chinese)\n"
            "- If official local company name exists (e.g., 株式会社メルカリ), use that exact form\n"
            "- When uncertain, default to '{brand_name} {topic}' pattern in target language\n\n"
            "NOTE: You MAY include an optional 'parent_area' string field per subtask when grouping by research areas is applicable.\n\n"
        )
```
?????????????????????????

??????
```text
        COMMON_DECOMPOSITION_SUFFIX = (
            "CRITICAL: Each subtask MUST have these EXACT fields: id, description, dependencies, estimated_tokens, suggested_tools, tool_parameters\n"
            "NEVER return null for subtasks field - always provide at least one subtask.\n\n"
            "TOOL SELECTION GUIDELINES:\n"
            "Default: Use NO TOOLS unless the task requires external data retrieval or computation.\n\n"
            "## WEB RESEARCH STRATEGY: Search First, Then Fetch\n\n"
            "### STEP 1 - SEARCH (discover relevant pages):\n"
            "- web_search: DEFAULT first step for any web research task\n"
            "  → Use for: 'find info about X', 'research Y', 'what is Z on site W'\n"
            "  → For specific domain: use site_filter parameter OR query='site:example.com [topic]'\n"
            "  → Returns: list of relevant URLs with snippets\n"
            "- CRITICAL: Search task response MUST include 'Top URLs:' section listing 3-8 most relevant URLs\n"
            "  (This is required because dependent tasks read URLs from your response text)\n\n"
            "### STEP 2 - FETCH (read content from search results):\n"
            "- web_fetch: Read single pages FROM SEARCH RESULTS\n"
            "  → Task depends on search task via dependencies field\n"
            "  → Agent reads URLs from search task's response, selects top 3-5 most relevant\n"
            "  → Example decomposition:\n"
            "    Task-1: web_search('site:cloudflare.com container pricing') → outputs Top URLs in response\n"
            "    Task-2: web_fetch (dependencies=['task-1']) → reads URLs from task-1 response, fetches them\n\n"
            "### DIRECT FETCH (skip search) ONLY WHEN:\n"
            "1. User provides SPECIFIC URL: 'read https://example.com/pricing' → web_fetch directly\n"
            "2. Quick homepage check: 'what does company X do' → web_fetch homepage only\n"
            "3. Following up on URL from previous conversation/search\n\n"
            "## THREE FETCH TOOLS - Choose Correctly:\n\n"
            "### web_fetch (single page):\n"
            "- USE: After search, to read specific URLs from search results\n"
            "- USE: When user provides exact URL (https://...)\n"
            "- NOT FOR: Discovering what pages exist on a site\n\n"
            "### web_subpage_fetch (multi-page targeted):\n"
            "- USE ONLY WHEN user specifies MULTIPLE explicit sections:\n"
            "  → 'get pricing, docs, and about pages from stripe.com'\n"
            "  → 'fetch /ir, /news, /press from tesla.com'\n"
            "- Set target_paths parameter with explicit paths: ['/pricing', '/docs', '/about']\n"
            "- NOT FOR: 'find info about X on site Y' (use search → fetch instead)\n"
            "- NOT FOR: Vague requests without explicit page names\n\n"
            "### web_crawl (multi-page exploratory):\n"
            "- USE ONLY WHEN user wants broad site discovery:\n"
            "  → 'crawl this site', 'explore the website', 'scan all pages'\n"
            "  → 'what pages does this site have', 'audit the domain'\n"
            "- USE: Unknown site structure, need comprehensive coverage\n"
            "- NOT FOR: Targeted research with specific questions\n\n"
            "## COMPANY/ENTITY RESEARCH WORKFLOW:\n"
            "1. Search first: '[company] [topic]' or use site_filter='[company].com' with query='[topic]'\n"
            "2. Include intent keywords in search: pricing, cost, plan, tier, 价格, 定价, 套餐 (if relevant)\n"
            "3. Fetch: Top relevant URLs from search results\n"
            "4. Business directories: 'site:crunchbase.com [company]', 'site:linkedin.com [company]'\n"
            "5. Asian companies: Include Japanese/Chinese name variants in searches\n\n"
            "## OTHER TOOLS:\n"
            "- calculator: For mathematical computations beyond basic arithmetic\n"
            "- file_read: When explicitly asked to read/open a specific local file\n"
            "- python_executor: For executing Python code, data analysis, or programming tasks\n"
            "- code_executor: ONLY for executing provided WASM code (do not use for Python)\n\n"
            "## Deep Research 2.0: Task Contracts (Optional, but REQUIRED for research workflows)\n"
            "For research workflows, you MAY include these fields to define explicit task boundaries:\n"
            "- output_format: {type: 'structured'|'narrative', required_fields: [...], optional_fields: [...]}\n"
            "- source_guidance: {required: ['official', 'aggregator'], optional: ['news'], avoid: ['social']}\n"
            "- search_budget: {max_queries: 5, max_fetches: 10}\n"
            "- boundaries: {in_scope: ['topic1', 'topic2'], out_of_scope: ['topic3']}\n\n"
            "Source type values: 'official' (company/.gov/.edu), 'aggregator' (crunchbase/wikipedia), "
            "'news' (recent articles), 'academic' (arxiv/papers), 'github', 'financial', 'local_cn', 'local_jp'\n\n"
            "Return ONLY valid JSON with this EXACT structure (no additional text):\n"
            "{\n"
            '  "mode": "standard",\n'
            '  "complexity_score": 0.5,\n'
            '  "subtasks": [\n'
            "    {\n"
            '      "id": "task-1",\n'
            '      "description": "Task description",\n'
            '      "dependencies": [],\n'
            '      "estimated_tokens": 500,\n'
            '      "suggested_tools": [],\n'
            '      "tool_parameters": {},\n'
            '      "output_format": {"type": "narrative", "required_fields": [], "optional_fields": []},\n'
            '      "source_guidance": {"required": ["official"], "optional": ["news"]},\n'
            '      "search_budget": {"max_queries": 10, "max_fetches": 20},\n'
            '      "boundaries": {"in_scope": ["topic"], "out_of_scope": []}\n'
            "    }\n"
            "  ],\n"
            '  "execution_strategy": "sequential",\n'
            '  "concurrency_limit": 1,\n'
            '  "token_estimates": {"task-1": 500},\n'
            '  "total_estimated_tokens": 500\n'
            "}\n\n"
            "CRITICAL: Tool parameters MUST use EXACT parameter names from schemas. See available tools below.\n\n"
            "IMPORTANT: Use python_executor for Python code execution tasks. Never suggest code_executor unless user\n"
            "explicitly provides WASM bytecode. For general code writing (without execution), handle directly.\n\n"
            f"{tool_schemas_text}\n\n"
            "Example for a stock query 'Analyze Apple stock trend':\n"
            "{\n"
            '  "mode": "standard",\n'
            '  "complexity_score": 0.5,\n'
            '  "subtasks": [\n'
            "    {\n"
            '      "id": "task-1",\n'
            '      "description": "Search for Apple stock trend analysis forecast",\n'
            '      "dependencies": [],\n'
            '      "estimated_tokens": 800,\n'
            '      "suggested_tools": ["web_search", "web_fetch"],\n'
            '      "tool_parameters": {"tool": "web_search", "query": "Apple stock AAPL trend analysis forecast"},\n'
            '      "output_format": {"type": "narrative", "required_fields": [], "optional_fields": []},\n'
            '      "source_guidance": {"required": ["news", "financial"], "optional": ["aggregator"]},\n'
            '      "search_budget": {"max_queries": 10, "max_fetches": 20},\n'
            '      "boundaries": {"in_scope": ["stock price", "market analysis"], "out_of_scope": ["company history"]}\n'
            "    }\n"
            "  ],\n"
            '  "execution_strategy": "sequential",\n'
            '  "concurrency_limit": 1,\n'
            '  "token_estimates": {"task-1": 800},\n'
            '  "total_estimated_tokens": 800\n'
            "}\n\n"
            "Rules:\n"
            '- mode: must be "simple", "standard", or "complex"\n'
            "- complexity_score: number between 0.0 and 1.0\n"
            "- dependencies: array of task ID strings or empty array []\n"
            "- suggested_tools: empty array [] if no tools needed, otherwise list tool names\n"
            "- tool_parameters: empty object {} if no tools, otherwise parameters for the tool\n"
            "- source_guidance: (optional) object with required/optional/avoid source type arrays\n"
            "- boundaries: (optional) object with in_scope/out_of_scope topic arrays\n"
            "- For subtasks with non-empty dependencies, DO NOT prefill tool_parameters; set it to {} and avoid placeholders (the agent will use previous_results to construct exact parameters).\n"
            "- Let the semantic meaning of the query guide tool selection\n"
        )
```
????????????????????????????

## ????

????????????????????????????????????????????????????????????

????????????????????????????????????????????????????

??????????????????????????????????????????????

????????????????????????????????????????????????

???????????????????????????????????????????????

???????????????????????????????????????????????

## ?????????????

??????????????????????????????????????????????????????

????????????????????????????????????????????

?????????????????????????????????????????????

?????????????????????????????????????

??????????????????????????????????

## ????

?????????????
- ????????????????????????????
- ????????????????????????????????????

?????????????
- ??????????????????????
- ????????????????
- ???????????????????

?????????????
- ????????????????????????????????????
- ???????????????????????????????

?????????????
- ???????????????????????
- ???????????????????????????????

??????????
- ????????????????????
- ?????????????????????????

?????????????
- ??????????????????
- ?????????????????????????????

