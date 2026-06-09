package com.ruleforge.console.model;

import com.ruleforge.console.repository.model.RepositoryFile;
import lombok.Data;

import java.util.List;

@Data
public class Repository {
    private RepositoryFile publicResource;
    private RepositoryFile rootFile;
    private List<String> projectNames;
}
