package com.ruleforge.console.controller;

import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.model.library.variable.VariableLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/variableeditor")
@RequiredArgsConstructor
public class VariableController {

    private final ExternalRepository externalRepository;

    @PostMapping("/generateVariableLibrary")
    public VariableLibrary generateVariableLibrary() {
        return this.externalRepository.generalEntityToVariableLibrary();
    }
}
