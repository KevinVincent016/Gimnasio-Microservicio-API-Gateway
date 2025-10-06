package com.gym.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumenDTO {
    
    private Object miembro;
    private List<Object> clases;
    private List<Object> entrenadores;
    private List<Object> equipos;
    private Map<String, String> errores;
    
    public ResumenDTO() {}
    
    public ResumenDTO(Object miembro, List<Object> clases, List<Object> entrenadores, List<Object> equipos) {
        this.miembro = miembro;
        this.clases = clases;
        this.entrenadores = entrenadores;
        this.equipos = equipos;
    }
    
    public Object getMiembro() {
        return miembro;
    }
    
    public void setMiembro(Object miembro) {
        this.miembro = miembro;
    }
    
    public List<Object> getClases() {
        return clases;
    }
    
    public void setClases(List<Object> clases) {
        this.clases = clases;
    }
    
    public List<Object> getEntrenadores() {
        return entrenadores;
    }
    
    public void setEntrenadores(List<Object> entrenadores) {
        this.entrenadores = entrenadores;
    }
    
    public List<Object> getEquipos() {
        return equipos;
    }
    
    public void setEquipos(List<Object> equipos) {
        this.equipos = equipos;
    }
    
    public Map<String, String> getErrores() {
        return errores;
    }
    
    public void setErrores(Map<String, String> errores) {
        this.errores = errores;
    }
    
    public void addError(String servicio, String mensaje) {
        if (this.errores == null) {
            this.errores = new java.util.HashMap<>();
        }
        this.errores.put(servicio, mensaje);
    }
}
