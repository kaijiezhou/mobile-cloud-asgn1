/*
 *
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.magnum.dataup;

import org.apache.commons.io.IOUtils;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import retrofit.http.Streaming;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class VideoServiceController {
    private final static String VIDEO_LOCATION_FMT = "http://%s:%d/video/%d/data";
    private final static String DATA_FOLDER_PATH = "data/%d/video.mpeg";
    // simulating a database or local storage
    private Map<Long, Video> videoStore = new ConcurrentHashMap<>();
    private Map<Long, VideoStatus> statusStore = new ConcurrentHashMap<>();
    private Map<Long, File> fileStore = new ConcurrentHashMap<>();
    private static AtomicLong curID = new AtomicLong();

    @RequestMapping(value = "/video", method = RequestMethod.GET)
    @ResponseBody
    public Collection<Video> getVideos() {
        return videoStore.values();
    }

    @RequestMapping(value = "/video", method = RequestMethod.POST)
    @ResponseBody
    public Video saveVideo(@RequestBody Video video) {
        if (video.getId() == 0) {
            video.setId(curID.incrementAndGet());
        }
        video.setDataUrl(newDataUrl(video.getId()));
        videoStore.put(video.getId(), video);
        return video;
    }

    @RequestMapping("/video/{id}/data")
    @ResponseBody
    public VideoStatus uploadVideo(@RequestParam("data") MultipartFile videoFile,
                                   @PathVariable("id") long id, HttpServletResponse response)
            throws IOException {
        if (id < 1 || !videoStore.containsKey(id)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        File file = new File(String.format(DATA_FOLDER_PATH, id));
        File folder = file.getParentFile();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        file.createNewFile();
        try (InputStream is = videoFile.getInputStream();
             FileOutputStream os = new FileOutputStream(file)) {
            VideoStatus status = new VideoStatus(VideoStatus.VideoState.PROCESSING);
            statusStore.put(id, status);
            IOUtils.copy(is, os);
            status.setState(VideoStatus.VideoState.READY);
            fileStore.put(id, file);
            status.setState(VideoStatus.VideoState.READY);
            return status;
        }
    }

    @Streaming
    @RequestMapping(value = "/video/{id}/data", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Resource> downloadVideo(@PathVariable("id") long id, HttpServletResponse response) {
        File file = fileStore.get(id);
        if (file == null) {
            return new ResponseEntity<Resource>(HttpStatus.NOT_FOUND);
        }
        Resource fileRc = new FileSystemResource(file);
        return new ResponseEntity<>(fileRc, HttpStatus.OK);

    }

    private String newDataUrl(long id) {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return String.format(VIDEO_LOCATION_FMT, request.getServerName(), request.getServerPort(), id);
    }


}
