package com.sky.controller.user;

import com.sky.dto.AddressBookDTO;
import com.sky.entity.AddressBook;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端-地址簿接口
 */
@RestController
@RequestMapping("/user/addressBook")
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    @PostMapping
    public Result<Void> add(@RequestBody AddressBookDTO addressBookDTO) {
        addressBookService.add(addressBookDTO);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<AddressBook>> list() {
        return Result.success(addressBookService.list());
    }

    @GetMapping("/default")
    public Result<AddressBook> getDefault() {
        return Result.success(addressBookService.getDefault());
    }

    @PutMapping("/default")
    public Result<Void> setDefault(Long id) {
        addressBookService.setDefault(id);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody AddressBookDTO addressBookDTO) {
        addressBookService.update(addressBookDTO);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> delete(Long id) {
        addressBookService.delete(id);
        return Result.success();
    }
}
