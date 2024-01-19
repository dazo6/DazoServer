package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.mapper.FanboxArtistMapper;
import com.dazo66.service.KemonoArtistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Dazo66
 */
@Service
public class KemonoArtistServiceImpl implements KemonoArtistService {

    @Autowired
    private FanboxArtistMapper fanboxArtistMapper;

    @Override
    public Page<Artist> getArtistByPage(int page, int pageSize,
                                        Wrapper<Artist> queryWrapper) {
        Page<Artist> fanboxArtistPage = Page.of(page, pageSize);
        return fanboxArtistMapper.selectPage(fanboxArtistPage, queryWrapper);
    }

    @Override
    public Artist addArtist(Artist artist) {
        fanboxArtistMapper.insert(artist);
        return artist;
    }

    @Override
    public Artist getArtistId(String artistId, ArtistDomainEnum domainEnum) {
        return fanboxArtistMapper.selectOne(new QueryWrapper<>(new Artist().setDomain(domainEnum).setArtistId(artistId)));
    }

    @Override
    public Artist disable(String artistId) {
        Artist artist = new Artist().setArtistId(artistId).setEnable(false);
        fanboxArtistMapper.update(artist, new QueryWrapper<Artist>().eq("artist_id",
                artistId));
        return artist;
    }

    @Override
    public Artist updateByArtistId(Artist artist) {
        if (artist.getArtistId() == null) {
            throw new RuntimeException("artist id must not null in update");
        }
        fanboxArtistMapper.update(artist, new QueryWrapper<Artist>().eq("artist_id", artist.getArtistId()));
        return artist;
    }
}
