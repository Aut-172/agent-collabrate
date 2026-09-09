package com.example.agentcollab.repository;

import com.example.agentcollab.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByIdIn(List<Long> ids);
}
