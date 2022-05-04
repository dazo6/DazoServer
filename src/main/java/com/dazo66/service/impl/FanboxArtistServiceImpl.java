package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.FanboxArtist;
import com.dazo66.mapper.FanboxArtistMapper;
import com.dazo66.service.FanboxArtistService;
import com.dazo66.util.LocalLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Dazo66
 */
@Service
@LocalLock(lockBeanName = "ioLockObject")
public class FanboxArtistServiceImpl implements FanboxArtistService {

    @Autowired
    private FanboxArtistMapper fanboxArtistMapper;

    @Override
    public Page<FanboxArtist> getArtistByPage(int page, int pageSize,
                                              QueryWrapper<FanboxArtist> queryWrapper) {
        Page<FanboxArtist> fanboxArtistPage = Page.of(page, pageSize);
        fanboxArtistPage.addOrder(OrderItem.desc("last_update"));
        return fanboxArtistMapper.selectPage(fanboxArtistPage, queryWrapper);
    }

    @Override
    public FanboxArtist addArtist(FanboxArtist artist) {
        fanboxArtistMapper.insert(artist);
        return artist;
    }

    @Override
    public FanboxArtist disable(String artistId) {
        FanboxArtist fanboxArtist = new FanboxArtist().setArtistId(artistId).setEnable(false);
        fanboxArtistMapper.updateById(fanboxArtist);
        return fanboxArtist;
    }

    @Override
    public FanboxArtist updateByArtistId(FanboxArtist artist) {
        if (artist.getArtistId() == null) {
            throw new RuntimeException("artist id must not null in update");
        }
        fanboxArtistMapper.update(artist,
                new QueryWrapper<>(new FanboxArtist().setArtistId(artist.getArtistId())));
        return null;
    }
}
