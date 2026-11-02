# bpmn-models

| Folder | Contents |
|---|---|
| `processes/` | BPMN processes (main approval, smart meter + tariff contract) |
| `decisions/` | DMN decision tables |
| `forms/` | Camunda forms for user tasks |
| `deploy/` | `start-all.ps1`, `stop-all.ps1`, `deploy.ps1` for local c8run |
| `diagrams/` | PNG exports for the report |

## Lint

```bash
cd bpmn-models
npm install
npm run lint
```

## Deploy (c8run running on localhost:8080)

```powershell
powershell -File bpmn-models\deploy\start-all.ps1
powershell -File bpmn-models\deploy\deploy.ps1
```
