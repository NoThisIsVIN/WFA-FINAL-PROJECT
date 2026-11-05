# DMN decision tests

Table-driven tests and a rule-coverage report for every decision in `bpmn-models/decisions/`.

Decisions run in-JVM on **zeebe-dmn 8.10.7**, the same DMN engine the Zeebe broker uses. Test variables are
encoded as MessagePack before evaluation, exactly as the broker stores process variables, so number and null
handling match production.

## Running

```bash
# all decision files
mvn -f backend-springboot/decision-tests/pom.xml test

# only some files (base names, comma-separated)
mvn -f backend-springboot/decision-tests/pom.xml test -Ddmn.files=connection-procedure,feed-in-tariff

# also deploy to the local cluster and check the REST evaluation API gives identical results
mvn -f backend-springboot/decision-tests/pom.xml test -Ddmn.remote=http://localhost:8080
```

The coverage report is written to `target/dmn-coverage/dmn-coverage.md` (and `.json`) and printed at the end of
the run. The build fails if any decision-table rule is not matched by at least one test case.

## What is checked

For every `.dmn` file:

- it parses with the Zeebe DMN engine;
- the `definitions` id equals the file name, and the file targets Camunda 8.10;
- decision ids are unique across all files;
- every rule id starts with `<DecisionId>_` and every rule has an annotation (`<description>`);
- a matching `<file>.cases.json` exists in `src/test/resources/dmn-cases/`.

Then every case and sweep in the cases files runs, and finally the **100% rule-coverage gate** is enforced.

## Cases file format

`src/test/resources/dmn-cases/<file>.cases.json`:

```json
{
  "dmnFile": "connection-procedure.dmn",
  "cases": [
    {
      "name": "SMALL homeowner 8 kVA -> STANDARD, 30 days",
      "decisionId": "ProcedureDetermination",
      "variables": { "system": { "inverterKva": 8, "pvKwp": 9.6, "hasBattery": false } },
      "expectedSubset": { "sizeClass": "SMALL", "type": "STANDARD", "slaDays": 30 },
      "expectedMatchedRules": { "SizeClass": ["SizeClass_Small"] },
      "refs": ["T01"]
    }
  ],
  "sweeps": [
    {
      "name": "SizeClass has no gaps or overlaps",
      "decisionId": "SizeClass",
      "vary": { "system.inverterKva": { "from": 0, "to": 300, "step": 0.05 } },
      "assert": "nonNull"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `expected` | Exact JSON equality with the decision output (numbers compared by value; dates are ISO strings). |
| `expectedSubset` | Every given key / element must match; extra object keys are allowed. |
| `expectNull` | The decision result must be `null`. |
| `expectFailure` (+ `failureContains`) | The evaluation must fail. |
| `expectedMatchedRules` | Decision id → exact ordered list of matched rule ids (also works for required decisions). |
| `refs` | Test / failure-path ids from the design docs that this case demonstrates. |
| `sweeps[].vary` | Dotted path → list of values or `{from, to, step}`; up to two paths (cartesian product). |
| `sweeps[].assert` | `noFailure` (no engine error, e.g. no UNIQUE overlap) or `nonNull` (also no null result). |

Each case needs exactly one of `expected`, `expectedSubset`, `expectNull` or `expectFailure`. Sweeps don't count
towards rule coverage, so every rule needs at least one explicit case.
