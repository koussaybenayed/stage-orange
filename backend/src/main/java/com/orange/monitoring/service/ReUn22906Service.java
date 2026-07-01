package com.orange.monitoring.service;

import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.repository.ReUn22906Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReUn22906Service {

    @Autowired
    private ReUn22906Repository repository;

    public List<ReUn22906> getFilteredIncidents() {
        return repository.findFiltered(
                "D\u00e9connexion",
                "Echec de connexion",
                "Lenteur",
                "MAXBOX 5G"
        );
    }
}
