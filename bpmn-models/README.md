# bpmn-models

| Folder | Contents |
|---|---|
| `bpmn/` | BPMN processes: `solar-connection-approval` (main) and `smart-meter-contract` (call activity) |
| `decisions/` | 6 DMN files (classification, technical assessment, battery check, inspection checklist, final approval, tariff rate) |
| `forms/` | 18 Camunda forms for the user tasks |
| `deploy/` | PowerShell scripts: `start-all.ps1`, `deploy.ps1`, `stop-all.ps1` |
| `diagrams/` | PNG exports for the report |

## Lint

```bash
cd bpmn-models
npm install
npx bpmnlint bpmn/*.bpmn      # same as: npm run lint
```

## Deploy

```powershell
# from the repository root
powershell -ExecutionPolicy Bypass -File bpmn-models\deploy\start-all.ps1   # Mailpit + c8run, demo connector secrets
powershell -ExecutionPolicy Bypass -File bpmn-models\deploy\deploy.ps1      # bpmn/ + decisions/ + forms/ in one deployment
powershell -ExecutionPolicy Bypass -File bpmn-models\deploy\stop-all.ps1
```

`start-all.ps1` looks for a `c8run-8.10*` folder under `-CamundaHome` (default `$env:CAMUNDA_HOME`, else
`$HOME\camunda`) and for `mailpit\mailpit.exe` in the same folder. It sets `SECRET_MOCKS_API_KEY=demo-mocks-key`
and `SECRET_ESIGN_HMAC_SECRET=demo-esign-secret` if they are not set; these are demo-only values.

Processes, decisions and forms must be deployed together, because the user tasks and business rule tasks use
`bindingType="deployment"`. Deploying all three folders from Camunda Modeler in one go works as well.
