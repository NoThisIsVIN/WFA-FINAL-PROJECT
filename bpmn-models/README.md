# bpmn-models

| Folder | Contents |
|---|---|
| `processes/` | BPMN processes (main approval, smart meter + tariff contract) |
| `decisions/` | DMN decision tables |
| `forms/` | Camunda forms for user tasks |
| `diagrams/` | PNG exports for the report |

## Lint

```bash
cd bpmn-models
npm install
npm run lint
```

## Deploy

Open the files in Camunda Modeler and deploy them to the local c8run (http://localhost:8080). Deploy the processes, decisions and forms together, because the user tasks link to forms in the same deployment.
