package com.sky.service;

import com.sky.dto.AddressBookDTO;
import com.sky.entity.AddressBook;

import java.util.List;

public interface AddressBookService {

    void add(AddressBookDTO addressBookDTO);

    List<AddressBook> list();

    AddressBook getDefault();

    void setDefault(Long id);

    void update(AddressBookDTO addressBookDTO);

    void delete(Long id);
}
