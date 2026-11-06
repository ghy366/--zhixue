package com.zhixue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "classrooms")
public class Classroom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_no", unique = true, nullable = false)
    private String roomNo;

    @Column(nullable = false)
    private String name;

    private Integer capacity;
    /** 上课地点（规范楼名，与 3D 沙盘 CAMPUS_BUILDINGS 对齐） */
    private String location;
    /** 所属楼栋（规范楼名） */
    private String building;
    /** 教室类型：普通教室 / 多媒体教室 / 阶梯教室 / 计算机实验室 等 */
    private String type;
    /** 所在楼层（与房号百位一致） */
    @Column(name = "floor_no")
    private Integer floorNo;
    private String equipment;
    private String status;

    public Classroom() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getFloorNo() { return floorNo; }
    public void setFloorNo(Integer floorNo) { this.floorNo = floorNo; }
    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
