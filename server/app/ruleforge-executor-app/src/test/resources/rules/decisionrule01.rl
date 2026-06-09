<?xml version="1.0" encoding="utf-8"?>
<rule-set>
    <import-variable-library path="/e2e_test/applicant.vl"/>
    <rule name="adult-check" salience="10">
        <if>
            <atom op="GreaterThen">
                <left var="age" var-label="年龄" var-category="ApplicantModel" datatype="Integer" type="variable"/>
                <value type="Input" datatype="Integer" content="18"/>
            </atom>
        </if>
        <then>
            <console-print>
                <value type="Input" datatype="String" content="adult-check FIRED"/>
            </console-print>
            <var-assign var="approved" var-label="是否通过" var-category="ApplicantModel" type="variable">
                <value type="Input" datatype="Boolean" content="true"/>
            </var-assign>
            <var-assign var="ruleResult" var-label="规则结果" var-category="OutputModel" type="variable">
                <value type="Input" datatype="String" content="PASS"/>
            </var-assign>
        </then>
    </rule>
</rule-set>
