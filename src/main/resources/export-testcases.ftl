<#-- @ftlvariable name="data" type="io.github.eroshenkoam.allure.model.TestCasesDto" -->
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC" crossorigin="anonymous"/>
    <style>
        .testcase {
            padding: 20px 0;
        }

        .steps {
            padding: 10px 0;
        }

        .step {
            padding: 2px 0;
        }
    </style>
</head>
<body style="font-family: Arial,serif">
<div class="container-fluid">
    <div class="row">
        <div class="col">
            <h1>Test Cases</h1>
            <#list data.testCases as testCase>
                <div class="testcase">
                    <h2>
                        <small class="text-muted">${testCase.id}</small>
                        ${testCase.name}
                    </h2>
                    <div class="steps">
                        <h3>Scenario</h3>
                        <@testCaseSteps testCase.getId() testCase.steps 0/>
                    </div>
                </div>
            </#list>
        </div>
    </div>
</div>
</body>
</html>

<#macro testCaseSteps testCaseId steps level>
    <ul class="list-group-numbered">
        <#list steps as step>
            <li class="step">
                <![CDATA[${step.name?html}]]>
                <#if step.attachments??>
                    <#list step.attachments as attachment>
                        <img src="attachments/${testCaseId}/${attachment.name}" class="img-fluid img-thumbnail" />
                    </#list>
                </#if>
                <@testCaseSteps testCaseId step.steps level+1/>
            </li>
        </#list>
    </ul>
</#macro>
