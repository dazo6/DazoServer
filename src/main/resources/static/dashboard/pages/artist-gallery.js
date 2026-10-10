(() => {
    "use strict";

    const {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} = Vue;
    const {useRoute, useRouter} = VueRouter;
    window.DazoAdminPages = window.DazoAdminPages || {};
    const GALLERY_SORT_STORAGE_PREFIX = "dazo-gallery-sort-v1:";
    const GALLERY_POSITION_STORAGE_PREFIX = "dazo-gallery-position-v1:";
    const GALLERY_RESUME_PROMPT_MS = 20000;
    const GALLERY_RESTORE_STABILIZE_MS = 20000;
    const IMAGE_LONG_PRESS_MS = 550;
    const IMAGE_LONG_PRESS_MOVE_PX = 10;

    window.DazoAdminPages.ArtistGallery = {
        template: "#artist-gallery-template",
        components: {
            GalleryVideoPlayer: {
                props: {slide: {type: Object, required: true}},
                emits: ["metadata", "error"],
                template: '<div class="cinema-viewer-video" @click.stop @dblclick.stop @pointerdown.stop @pointermove.stop @pointerup.stop @pointercancel.stop @touchstart.stop @touchmove.stop @touchend.stop @touchcancel.stop @contextmenu.stop><div ref="host" class="gallery-player-host"></div></div>',
                setup(props, {emit}) {
                    const host = ref(null);
                    let player = null;
                    onMounted(() => {
                        try {
                            player = new window.Player({
                                el: host.value, url: props.slide.image.url,
                                poster: props.slide.image.thumbnailUrl || "",
                                width: "100%", height: "100%", lang: "zh-cn",
                                autoplay: true, playsinline: true, videoFillMode: "contain",
                                videoAttributes: {preload: "metadata"},
                                keyShortcut: false, playbackRate: [1, 1.5, 2, 3],
                                mobile: {disablePress: false, pressRate: 3, closedbClick: false}
                            });
                            player.on("loadedmetadata", () => emit("metadata", {currentTarget: player.video}));
                            player.on("error", () => emit("error"));
                            player.on("autoplay_was_prevented", () => {
                                const blockedPlayer = player;
                                // Xgplayer pauses after emitting this event; retry after that handler finishes.
                                Promise.resolve().then(() => {
                                    if (!player || player !== blockedPlayer || player.muted) return;
                                    player.muted = true;
                                    return player.play();
                                }).catch(() => {
                                    // Keep the play button available if the browser also blocks muted playback.
                                });
                            });
                        } catch (error) { emit("error"); }
                    });
                    onBeforeUnmount(() => {
                        if (player) { player.pause(); player.destroy(); player = null; }
                    });
                    return {host};
                }
            }
        },
        setup() {
            const route = useRoute();
            const router = useRouter();
            const artist = reactive({
                name: "",
                icon: "",
                banner: "",
                url: ""
            });
            const folderPath = ref(queryValue(route.query.folderPath));
            const uploadInput=ref(null),uploading=ref(false),uploadProgress=ref(0);
            const canUpload=computed(()=>window.DazoAuth.can("update",folderPath.value));
            let uploadRequest=null,uploadDisposed=false;
            const downloading=ref(false);
            const canDownload=computed(()=>window.DazoAuth.can("read",folderPath.value));
            let downloadRequest=null;
            async function downloadAll(){
                if(downloading.value || !canDownload.value)return;
                downloading.value=true;
                const destination=folderPath.value;
                const controller=new AbortController();downloadRequest=controller;
                try{
                    const archive=await window.DazoAuth.request("/api/fanbox/gallery/archive?"+new URLSearchParams({folderPath:destination}),{signal:controller.signal});
                    if(controller.signal.aborted || uploadDisposed || folderPath.value!==destination)return;
                    const link=document.createElement("a");link.href=archive.url;link.download=archive.filename;link.hidden=true;
                    document.body.appendChild(link);link.click();link.remove();
                }catch(e){if(e.name!=="AbortError" && !uploadDisposed)ElementPlus.ElMessage.error(e.message);}
                finally{if(downloadRequest===controller){downloadRequest=null;downloading.value=false;}}
            }

            function sortStorageKey(){return GALLERY_SORT_STORAGE_PREFIX+String(folderPath.value).replace(/\\/g,"/").toLowerCase().replace(/\/+$/,"");}
            function readSavedSort(){try{const value=JSON.parse(window.localStorage.getItem(sortStorageKey()));return value && ["album","created","name","random"].includes(value.field) && ["asc","desc"].includes(value.order)?value:null;}catch{return null;}}
            async function uploadFile(event){
                const file=event.target.files[0];event.target.value="";
                if(!file || uploading.value)return;
                if(file.size>500*1024*1024){ElementPlus.ElMessage.error("文件最大500MB");return;}
                if(!canUpload.value){ElementPlus.ElMessage.error("没有当前目录的上传权限");return;}
                const destination=folderPath.value;uploading.value=true;uploadProgress.value=0;
                try{
                    await window.DazoAuth.ready;
                    async function sendPart(url,body,start,bytes){
                        return new Promise((resolve,reject)=>{
                            const xhr=new XMLHttpRequest();uploadRequest=xhr;
                            xhr.open("POST",url);xhr.setRequestHeader("X-CSRF-Token",window.DazoAuth.state.csrf);
                            xhr.upload.onprogress=e=>{if(e.lengthComputable)uploadProgress.value=Math.round((start+e.loaded/e.total*bytes)/file.size*100);};
                            xhr.onload=()=>{let result;try{result=JSON.parse(xhr.responseText);}catch{reject(new Error("HTTP "+xhr.status));return;}if(xhr.status>=200 && xhr.status<300 && result.result==="SUCCESS")resolve(result.data);else reject(new Error(result.message || "HTTP "+xhr.status));};
                            xhr.onerror=()=>reject(new Error("上传连接失败，请重试"));xhr.onabort=()=>reject(new Error("上传已取消"));xhr.send(body);
                        });
                    }
                    const chunkSize=512*1024;
                    if(file.size<=chunkSize){
                        const body=new FormData();body.append("file",file);
                        await sendPart("/api/fanbox/gallery/upload?"+new URLSearchParams({folderPath:destination}),body,0,file.size);
                    }else{
                        const count=Math.ceil(file.size/chunkSize);let uploadId="";
                        for(let index=0;index<count;index++){
                            if(uploadDisposed)throw new Error("上传已取消");
                            const start=index*chunkSize,end=Math.min(file.size,start+chunkSize);
                            const body=new FormData();body.append("file",file.slice(start,end),file.name);
                            const params=new URLSearchParams({folderPath:destination,filename:file.name,totalSize:String(file.size),partIndex:String(index),partCount:String(count)});
                            if(uploadId)params.set("uploadId",uploadId);
                            const result=await sendPart("/api/fanbox/gallery/upload-chunk?"+params,body,start,end-start);uploadId=result.uploadId;
                        }
                    }
                    uploadProgress.value=100;
                    if(!uploadDisposed)ElementPlus.ElMessage.success("上传完成，索引已刷新");
                }catch(e){if(!uploadDisposed)ElementPlus.ElMessage.error(e.message);}
                finally{uploadRequest=null;uploading.value=false;}
            }
            const galleryGrid = ref(null);
            const images = ref([]);
            const renderedImageCount = ref(0);
            const visibleImages = computed(() => images.value.slice(0, renderedImageCount.value));
            const loading = ref(false);
            const errorMessage = ref("");
            const viewerVisible = ref(false);
            const viewerIndex = ref(0);
            const viewerRoot = ref(null);
            const viewerSlides = ref([]);
            const viewerScale = ref(1);
            const viewerOffset = reactive({x: 0, y: 0});
            const viewerTransition = ref(true);
            const viewerSwipeOffset = ref(0);
            const viewerSwipeTransition = ref(false);
            const viewerSwipeDuration = ref(260);
            const viewerSlideAnimating = ref(false);
            let savedGalleryPosition = readGalleryPosition();
            const savedSort = readSavedSort();
            const sort = reactive({
                field: savedSort ? savedSort.field : savedGalleryPosition && ["album", "created", "name", "random"].includes(savedGalleryPosition.sortField)
                    ? savedGalleryPosition.sortField : "album",
                order: savedSort ? savedSort.order : savedGalleryPosition && ["asc", "desc"].includes(savedGalleryPosition.sortOrder)
                    ? savedGalleryPosition.sortOrder : "desc"
            });
            const resumePromptVisible = ref(false);
            const resumePosition = ref(null);
            const imageInfoVisible = ref(false);
            const imageInfo = reactive({
                name: "",
                url: "",
                typeText: "图片",
                sizeText: "读取中…",
                sizeBytes: 0,
                modifiedText: "",
                dimensionsLabel: "",
                dimensionsText: "",
                durationText: ""
            });
            const artistName = computed(() => artist.name || "画师图库");
            const hasArtist = computed(() => Boolean(artist.name));
            const currentImage = computed(() => images.value[viewerIndex.value] || null);
            const viewerImageStyle = computed(() => ({
                transform: `translate(${viewerOffset.x}px, ${viewerOffset.y}px) scale(${viewerScale.value})`,
                transition: viewerTransition.value
                    ? "transform .22s ease-out, opacity .14s ease"
                    : "opacity .14s ease"
            }));
            const viewerTrackStyle = computed(() => ({
                transform: `translateX(calc(-100vw + ${viewerSwipeOffset.value}px))`,
                transition: viewerSwipeTransition.value
                    ? `transform ${viewerSwipeDuration.value}ms cubic-bezier(.22, .61, .36, 1)` : "none"
            }));

            let layoutFrame = 0;
            let galleryBatchFrame = 0;
            let positionSaveTimer = 0;
            let resumePromptTimer = 0;
            let restoreDeadlineTimer = 0;
            let restoreStableTimer = 0;
            let activeRestorePosition = null;
            let pendingViewerGalleryReturn = null;
            let resumeOfferConsumed = false;
            let imageLongPressTimer = 0;
            let galleryLongPressState = null;
            let viewerMouseLongPressState = null;
            let suppressGalleryClickUntil = 0;
            let imageInfoRequestId = 0;
            let viewerCloseTimer = 0;
            let lastTouchTapAt = 0;
            let suppressMouseClickUntil = 0;
            let touchState = null;
            let viewerScrollLocked = false;
            let viewerScrollX = 0;
            let viewerScrollY = 0;
            let previousBodyStyles = null;
            let viewerHistoryEntry = false;
            let viewerOpeningVisibleUrls = new Set();
            let viewerSlideTimer = 0;
            let pendingViewerDirection = 0;
            let viewerSlideId = 0;
            let viewerCompletingSlide = false;
            let pendingViewerFolderNavigation = false;
            let galleryLoadId = 0;
            const MAX_FORWARD_PRELOAD = 10;
            const PRELOAD_CONCURRENCY = 3;
            const preloadPromises = new Map();
            let preloadQueue = [];
            let preloadWindowUrls = new Set();
            let preloadActiveCount = 0;
            const GALLERY_BATCH_SIZE = 30;
            const GALLERY_LOAD_AHEAD_PX = 900;

            async function loadGallery(offerResume = true) {
                const loadId = ++galleryLoadId;
                loading.value = true;
                errorMessage.value = "";
                let loaded = false;
                try {
                    const params = new URLSearchParams({sortBy: sort.field, order: sort.order});
                    if (!folderPath.value) {
                        throw new Error("未指定文件夹路径");
                    }
                    params.set("folderPath", folderPath.value);
                    const data = await requestData(`/api/fanbox/gallery?${params}`);
                    if (loadId !== galleryLoadId) return false;
                    folderPath.value = data && data.folderPath ? data.folderPath : folderPath.value;
                    const previousItems=new Map(images.value.map(item=>[galleryItemKey(item),item]));
                    images.value = data && Array.isArray(data.images)
                        ? data.images.map(image => {
                            if (image.type !== "folder") {
                                const params = new URLSearchParams({folderPath: folderPath.value, path: image.name});
                                const url = `/api/fanbox/gallery/file?${params}`;
                                image = {...image, url, thumbnailUrl: `${url}&thumbnail=true`};
                            }
                            const old=previousItems.get(galleryItemKey(image));
                            const previous=old && old.thumbnailUrl===image.thumbnailUrl?old:null;
                            return ({
                            ...image,
                            type: ["video", "folder"].includes(image.type) ? image.type : "image",
                            loaded: image.type === "folder" || Boolean(previous?.loaded),
                            loadError: Boolean(previous?.loadError),
                            durationText: previous?.durationText || "",
                            aspectRatio: previous?.aspectRatio || (image.type === "video" ? 16 / 9
                                : image.type === "folder" ? 4 / 3 : image.aspectRatio)
                        });})
                        : [];
                    renderedImageCount.value = Math.min(GALLERY_BATCH_SIZE, images.value.length);
                    viewerIndex.value = Math.min(viewerIndex.value, Math.max(images.value.length - 1, 0));
                    scheduleGalleryLayout();
                    scheduleGalleryBatchCheck();
                    loaded = true;
                    return true;
                } catch (error) {
                    if (loadId !== galleryLoadId) return false;
                    errorMessage.value = `图库加载失败：${error.message}`;
                    return false;
                } finally {
                    if (loadId === galleryLoadId) loading.value = false;
                    if (loaded && loadId === galleryLoadId) {
                        nextTick(() => {
                            if (loadId !== galleryLoadId || route.name !== "artist-gallery") return;
                            const restoring = window.DazoAdminScrollRestoration?.contentReady(
                                route.fullPath, restoreGalleryBackPosition);
                            if (offerResume && !restoring) offerSavedGalleryPosition();
                        });
                    }
                }
            }

            async function loadArtist() {
                const artistId = queryValue(route.query.artistId);
                if (!artistId) return;
                try {
                    const data = await requestData(`/api/fanbox/${encodeURIComponent(artistId)}`);
                    if (!data) return;
                    artist.name = data.name || "";
                    artist.icon = data.icon || "";
                    artist.banner = data.banner || "";
                    artist.url = data.url || "";
                } catch (error) {
                    ElementPlus.ElMessage.warning(`作者信息加载失败：${error.message}`);
                }
            }

            function openViewer(index) {
                if (!images.value.length) return;
                viewerOpeningVisibleUrls = captureVisibleGalleryUrls();
                saveGalleryPosition();
                clearViewerCloseTimer();
                viewerIndex.value = normalizeViewerIndex(index);
                rebuildViewerSlides();
                resetViewerTransform(false);
                resetViewerTrack(false);
                if (!viewerHistoryEntry) {
                    window.history.pushState(
                        {...(window.history.state || {}), dazoViewer: true},
                        "",
                        window.location.href
                    );
                    viewerHistoryEntry = true;
                }
                viewerVisible.value = true;
                lockViewerScroll();
                nextTick(() => {
                    if (viewerRoot.value) viewerRoot.value.focus({preventScroll: true});
                    window.requestAnimationFrame(() => {
                        viewerSwipeTransition.value = true;
                    });
                });
                preloadViewerImages(viewerIndex.value);
            }

            function handleGalleryItemClick(index) {
                if (Date.now() < suppressGalleryClickUntil) return;
                const item = images.value[index];
                if (item && item.type === "folder") {
                    enterFolder(item, false);
                    return;
                }
                openViewer(index);
            }

            async function enterFolder(item, keepViewerOpen) {
                if (!item || item.type !== "folder" || !item.folderPath) return;
                clearViewerCloseTimer();
                saveGalleryPosition();
                pendingViewerFolderNavigation = Boolean(keepViewerOpen);
                const query = {...route.query, folderPath: item.folderPath};
                try {
                    if (keepViewerOpen) {
                        await router.replace({name: "artist-gallery", query});
                    } else {
                        await router.push({name: "artist-gallery", query});
                    }
                } catch (error) {
                    pendingViewerFolderNavigation = false;
                    ElementPlus.ElMessage.error(`进入文件夹失败：${error.message}`);
                }
            }

            function startGalleryImageLongPress(event, image) {
                if (image && image.type === "folder") return;
                if (!event || !event.isPrimary || event.button !== 0) return;
                clearImageLongPressTimer();
                const element = event.currentTarget
                    ? event.currentTarget.querySelector("img, video") : null;
                galleryLongPressState = {
                    pointerId: event.pointerId,
                    startX: event.clientX,
                    startY: event.clientY,
                    longPressed: false
                };
                const state = galleryLongPressState;
                imageLongPressTimer = window.setTimeout(() => {
                    imageLongPressTimer = 0;
                    if (galleryLongPressState !== state) return;
                    state.longPressed = true;
                    suppressGalleryClickUntil = Date.now() + 900;
                    showImageInfo(image, element, false);
                }, IMAGE_LONG_PRESS_MS);
            }

            function trackGalleryImageLongPress(event) {
                const state = galleryLongPressState;
                if (!state || state.pointerId !== event.pointerId || state.longPressed) return;
                if (Math.hypot(event.clientX - state.startX, event.clientY - state.startY)
                    > IMAGE_LONG_PRESS_MOVE_PX) {
                    cancelGalleryImageLongPress();
                }
            }

            function finishGalleryImageLongPress(event) {
                const state = galleryLongPressState;
                if (!state || state.pointerId !== event.pointerId) return;
                const longPressed = state.longPressed;
                cancelGalleryImageLongPress();
                if (longPressed) {
                    event.preventDefault();
                    event.stopPropagation();
                }
            }

            function cancelGalleryImageLongPress() {
                clearImageLongPressTimer();
                galleryLongPressState = null;
            }

            function clearImageLongPressTimer() {
                if (!imageLongPressTimer) return;
                window.clearTimeout(imageLongPressTimer);
                imageLongPressTimer = 0;
            }

            function showImageInfo(image, element, originalDimensions) {
                if (!image || image.type === "folder") return;
                const requestId = ++imageInfoRequestId;
                const video = image.type === "video";
                imageInfo.name = imageDisplayName(image);
                imageInfo.url = image.url || "";
                imageInfo.typeText = video ? "视频" : "图片";
                imageInfo.sizeText = "读取中…";
                imageInfo.sizeBytes = 0;
                imageInfo.modifiedText = "读取中…";
                imageInfo.durationText = video && element && Number.isFinite(element.duration)
                    ? formatDuration(element.duration) : "";
                const videoElement = video && element && element.tagName === "VIDEO";
                const width = videoElement ? element.videoWidth : element && element.naturalWidth;
                const height = videoElement ? element.videoHeight : element && element.naturalHeight;
                if (width > 0 && height > 0) {
                    imageInfo.dimensionsLabel = videoElement ? "视频分辨率"
                        : video ? "首帧预览分辨率"
                            : originalDimensions ? "原图分辨率" : "预览分辨率";
                    imageInfo.dimensionsText = `${width} × ${height} px`;
                } else {
                    imageInfo.dimensionsLabel = "";
                    imageInfo.dimensionsText = "";
                }
                imageInfoVisible.value = true;
                readBrowserFileDetails(image.url, video).then(({size,modifiedAt}) => {
                    if (requestId !== imageInfoRequestId || !imageInfoVisible.value) return;
                    imageInfo.modifiedText = Number.isFinite(modifiedAt) ? new Date(modifiedAt).toLocaleString("zh-CN",{hour12:false}) : "浏览器无法读取";
                    imageInfo.sizeBytes = size || 0;
                    imageInfo.sizeText = size ? formatFileSize(size) : "浏览器无法读取";
                });
            }

            function closeImageInfo() {
                imageInfoRequestId += 1;
                imageInfoVisible.value = false;
            }

            function imageDisplayName(image) {
                if (image.name) return image.name;
                try {
                    const url = new URL(image.url, window.location.href);
                    const pathName = url.searchParams.get("path");
                    return pathName || decodeURIComponent(url.pathname.split("/").pop()) || "未命名文件";
                } catch (error) {
                    return "未命名文件";
                }
            }

            function galleryItemKey(image) {
                return image ? image.folderPath || image.url || image.name || "" : "";
            }

            function formatDuration(seconds) {
                if (!Number.isFinite(seconds) || seconds < 0) return "";
                const totalSeconds = Math.round(seconds);
                const hours = Math.floor(totalSeconds / 3600);
                const minutes = Math.floor(totalSeconds % 3600 / 60);
                const remainingSeconds = totalSeconds % 60;
                return hours > 0
                    ? `${hours}:${String(minutes).padStart(2, "0")}:${String(remainingSeconds).padStart(2, "0")}`
                    : `${minutes}:${String(remainingSeconds).padStart(2, "0")}`;
            }

            async function readBrowserFileDetails(url, requireFullLength = false) {
                // Video metadata is normally fetched with a byte range, so its Resource
                // Timing size is only the downloaded prefix rather than the full file.
                const timingSize = requireFullLength ? 0 : readResourceTimingSize(url);

                try {
                    const response = await fetch(url, {
                        method: "HEAD",
                        credentials: "same-origin",
                        cache: "no-store"
                    });
                    if (!response.ok) return {size:timingSize,modifiedAt:NaN};
                    const contentLength = Number.parseInt(response.headers.get("Content-Length"), 10);
                    return {size:Number.isFinite(contentLength) && contentLength > 0 ? contentLength : timingSize,modifiedAt:Date.parse(response.headers.get("Last-Modified"))};
                } catch (error) {
                    return {size:timingSize,modifiedAt:NaN};
                }
            }

            function readResourceTimingSize(url) {
                if (!window.performance || typeof window.performance.getEntriesByName !== "function") return 0;
                try {
                    const absoluteUrl = new URL(url, window.location.href).href;
                    const entries = window.performance.getEntriesByName(absoluteUrl);
                    for (let index = entries.length - 1; index >= 0; index -= 1) {
                        const entry = entries[index];
                        if (Number(entry.responseStatus) > 0
                            && (Number(entry.responseStatus) < 200 || Number(entry.responseStatus) >= 300)) {
                            continue;
                        }
                        const size = Number(entry.decodedBodySize) || Number(entry.encodedBodySize) || 0;
                        if (size > 0) return size;
                    }
                } catch (error) {
                    return 0;
                }
                return 0;
            }

            function formatFileSize(bytes) {
                if (!Number.isFinite(bytes) || bytes <= 0) return "浏览器无法读取";
                const units = ["B", "KB", "MB", "GB"];
                let value = bytes;
                let unitIndex = 0;
                while (value >= 1024 && unitIndex < units.length - 1) {
                    value /= 1024;
                    unitIndex += 1;
                }
                const digits = unitIndex === 0 || value >= 100 ? 0 : value >= 10 ? 1 : 2;
                return `${value.toFixed(digits)} ${units[unitIndex]}`;
            }

            function closeViewer(fromHistory = false) {
                if (!viewerVisible.value) return;
                prepareViewerGalleryReturn();
                closeImageInfo();
                clearViewerMouseLongPress();
                pauseViewerVideos();
                clearViewerCloseTimer();
                clearViewerSlideTimer();
                const removeViewerHistory = viewerHistoryEntry && !fromHistory;
                viewerHistoryEntry = false;
                preloadQueue = [];
                preloadWindowUrls = new Set();
                prunePreloadEntries(new Set());
                viewerVisible.value = false;
                touchState = null;
                if (removeViewerHistory) window.history.back();
            }

            function prepareViewerGalleryReturn() {
                const image = currentImage.value;
                if (!image) {
                    pendingViewerGalleryReturn = null;
                    return;
                }
                const index = images.value.indexOf(image);
                if (index < 0) {
                    pendingViewerGalleryReturn = null;
                    return;
                }
                if (viewerOpeningVisibleUrls.has(galleryItemKey(image))) {
                    pendingViewerGalleryReturn = null;
                    return;
                }
                const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                pendingViewerGalleryReturn = {
                    imageUrl: galleryItemKey(image),
                    index,
                    offsetTop: Math.min(140, Math.max(72, viewportHeight * .18))
                };
                renderedImageCount.value = Math.max(renderedImageCount.value,
                    Math.min(images.value.length, index + GALLERY_BATCH_SIZE));
            }

            function captureVisibleGalleryUrls() {
                const urls = new Set();
                const grid = galleryGrid.value;
                if (!grid) return urls;
                const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
                const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                grid.querySelectorAll(".gallery-item").forEach(element => {
                    const rect = element.getBoundingClientRect();
                    if (rect.width <= 0 || rect.height <= 0 || rect.right <= 0
                        || rect.left >= viewportWidth || rect.bottom <= 0 || rect.top >= viewportHeight) {
                        return;
                    }
                    const index = Number.parseInt(element.dataset.galleryIndex, 10);
                    const image = images.value[index];
                    if (image) urls.add(galleryItemKey(image));
                });
                return urls;
            }

            function handleViewerPopstate() {
                if (viewerVisible.value && viewerHistoryEntry) closeViewer(true);
            }

            function handleViewerAfterLeave() {
                viewerSlides.value = [];
                const returnPosition = pendingViewerGalleryReturn;
                pendingViewerGalleryReturn = null;
                viewerOpeningVisibleUrls = new Set();
                unlockViewerScroll(!returnPosition);
                resetViewerTransform(false);
                resetViewerTrack(false);
                if (returnPosition) {
                    stopGalleryPositionRestore(false);
                    activeRestorePosition = returnPosition;
                    layoutGalleryItems();
                    restoreDeadlineTimer = window.setTimeout(
                        () => stopGalleryPositionRestore(true), GALLERY_RESTORE_STABILIZE_MS);
                }
            }

            function lockViewerScroll() {
                if (viewerScrollLocked) return;
                const bodyStyle = document.body.style;
                viewerScrollX = window.scrollX || document.documentElement.scrollLeft || 0;
                viewerScrollY = window.scrollY || document.documentElement.scrollTop || 0;
                previousBodyStyles = {
                    overflow: bodyStyle.overflow,
                    position: bodyStyle.position,
                    top: bodyStyle.top,
                    left: bodyStyle.left,
                    right: bodyStyle.right,
                    width: bodyStyle.width,
                    boxSizing: bodyStyle.boxSizing,
                    paddingRight: bodyStyle.paddingRight
                };
                const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
                const layoutWidth = document.documentElement.clientWidth || viewportWidth;
                const scrollbarWidth = Math.max(0, viewportWidth - layoutWidth);
                const computedPaddingRight = Number.parseFloat(
                    window.getComputedStyle(document.body).paddingRight) || 0;
                bodyStyle.position = "fixed";
                bodyStyle.top = `-${viewerScrollY}px`;
                bodyStyle.left = `-${viewerScrollX}px`;
                bodyStyle.right = "0";
                bodyStyle.boxSizing = "border-box";
                bodyStyle.paddingRight = `${computedPaddingRight + scrollbarWidth}px`;
                bodyStyle.width = "100%";
                bodyStyle.overflow = "hidden";
                viewerScrollLocked = true;
            }

            function unlockViewerScroll(restoreSavedPosition = true) {
                if (!viewerScrollLocked) return;
                const bodyStyle = document.body.style;
                const savedStyles = previousBodyStyles || {};
                bodyStyle.overflow = savedStyles.overflow || "";
                bodyStyle.position = savedStyles.position || "";
                bodyStyle.top = savedStyles.top || "";
                bodyStyle.left = savedStyles.left || "";
                bodyStyle.right = savedStyles.right || "";
                bodyStyle.width = savedStyles.width || "";
                bodyStyle.boxSizing = savedStyles.boxSizing || "";
                bodyStyle.paddingRight = savedStyles.paddingRight || "";
                viewerScrollLocked = false;
                previousBodyStyles = null;
                if (!restoreSavedPosition) return;
                window.scrollTo(viewerScrollX, viewerScrollY);
                // Safari can apply its history scroll restoration after popstate.
                // Re-assert the saved position once the current frame has settled.
                window.requestAnimationFrame(() => {
                    if (!viewerVisible.value) window.scrollTo(viewerScrollX, viewerScrollY);
                });
            }

            function normalizeViewerIndex(index) {
                const count = images.value.length;
                if (!count) return 0;
                return ((Number(index) || 0) % count + count) % count;
            }

            function previousViewerImage() {
                startViewerSlide(-1);
            }

            function nextViewerImage() {
                startViewerSlide(1);
            }

            function viewportWidth() {
                return Math.max(window.innerWidth || document.documentElement.clientWidth || 1, 1);
            }

            function imageAt(index) {
                return images.value.length ? images.value[normalizeViewerIndex(index)] : null;
            }

            function createViewerSlide(image, originalRequested = false) {
                viewerSlideId += 1;
                return {
                    id: viewerSlideId,
                    image,
                    originalRequested,
                    originalReady: Boolean(image && image.type === "folder"),
                    loadError: false
                };
            }

            function rebuildViewerSlides() {
                if (!images.value.length) {
                    viewerSlides.value = [];
                    return;
                }
                viewerSlides.value = [
                    createViewerSlide(imageAt(viewerIndex.value - 1)),
                    createViewerSlide(imageAt(viewerIndex.value), true),
                    createViewerSlide(imageAt(viewerIndex.value + 1))
                ];
            }

            function requestViewerSlideOriginal(direction) {
                const slides = viewerSlides.value;
                if (slides.length !== 3) return;
                const slide = direction < 0 ? slides[0] : slides[2];
                if (!slide || !slide.image || ["folder", "video"].includes(slide.image.type)
                    || slide.originalRequested) return;
                slide.originalRequested = true;
                slide.originalReady = false;
            }

            function resetViewerTrack(enableTransition = true) {
                clearViewerSlideTimer();
                pendingViewerDirection = 0;
                viewerCompletingSlide = false;
                viewerSlideAnimating.value = false;
                viewerSwipeTransition.value = enableTransition;
                viewerSwipeDuration.value = 260;
                viewerSwipeOffset.value = 0;
            }

            function startViewerSlide(direction) {
                if (images.value.length < 2 || viewerSlideAnimating.value) return;
                clearViewerCloseTimer();
                pauseViewerVideos();
                resetViewerTransform(true);
                pendingViewerDirection = direction < 0 ? -1 : 1;
                requestViewerSlideOriginal(pendingViewerDirection);
                viewerSlideAnimating.value = true;
                const width = viewportWidth();
                const remainingRatio = Math.max(0, width - Math.abs(viewerSwipeOffset.value)) / width;
                viewerSwipeDuration.value = Math.max(120, Math.round(260 * remainingRatio));
                viewerSwipeTransition.value = true;
                viewerSwipeOffset.value = -pendingViewerDirection * width;
                armViewerSlideTimer();
            }

            function settleViewerTrack() {
                pendingViewerDirection = 0;
                viewerSlideAnimating.value = true;
                viewerSwipeDuration.value = Math.max(120,
                    Math.round(220 * Math.abs(viewerSwipeOffset.value) / viewportWidth()));
                viewerSwipeTransition.value = true;
                viewerSwipeOffset.value = 0;
                armViewerSlideTimer();
            }

            function armViewerSlideTimer() {
                clearViewerSlideTimer();
                viewerSlideTimer = window.setTimeout(completeViewerSlide, 340);
            }

            function clearViewerSlideTimer() {
                if (!viewerSlideTimer) return;
                window.clearTimeout(viewerSlideTimer);
                viewerSlideTimer = 0;
            }

            function handleViewerTrackTransitionEnd(event) {
                if (event.propertyName === "transform") completeViewerSlide();
            }

            async function completeViewerSlide() {
                clearViewerSlideTimer();
                if (!viewerSlideAnimating.value || viewerCompletingSlide) return;
                const direction = pendingViewerDirection;
                if (!direction) {
                    viewerSlideAnimating.value = false;
                    return;
                }

                viewerCompletingSlide = true;
                viewerSwipeTransition.value = false;
                // First commit transition:none while the track is still at the completed
                // position. Updating the slides and resetting the track in the same Vue
                // patch can make browsers briefly animate the reset and expose the black
                // slide background.
                await nextTick();
                if (!viewerVisible.value || !viewerSlideAnimating.value
                    || pendingViewerDirection !== direction) {
                    viewerCompletingSlide = false;
                    return;
                }

                viewerSwipeOffset.value = 0;
                const nextIndex = normalizeViewerIndex(viewerIndex.value + direction);
                const slides = viewerSlides.value;
                viewerIndex.value = nextIndex;
                if (slides.length === 3 && direction > 0) {
                    viewerSlides.value = [
                        slides[1],
                        slides[2],
                        createViewerSlide(imageAt(nextIndex + 1))
                    ];
                } else if (slides.length === 3) {
                    viewerSlides.value = [
                        createViewerSlide(imageAt(nextIndex - 1)),
                        slides[0],
                        slides[1]
                    ];
                } else {
                    rebuildViewerSlides();
                }
                viewerSlides.value.forEach((slide, index) => {
                    if (!slide || !slide.image || slide.image.type !== "video") return;
                    if (index === 1) {
                        if (!slide.originalRequested) slide.loadError = false;
                        slide.originalRequested = true;
                    } else {
                        slide.originalRequested = false;
                        slide.originalReady = false;
                    }
                });
                pendingViewerDirection = 0;
                resetViewerTransform(false);

                // Keep the already-rendered adjacent slide in the centre, then restore
                // transition handling only after the DOM has completed the silent reset.
                await nextTick();
                if (!viewerVisible.value) {
                    viewerCompletingSlide = false;
                    return;
                }
                viewerSlideAnimating.value = false;
                viewerCompletingSlide = false;
                preloadViewerImages(viewerIndex.value);
                window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
                    if (viewerVisible.value && !viewerSlideAnimating.value) {
                        viewerSwipeTransition.value = true;
                    }
                }));
            }

            function resetViewerTransform(animate = true) {
                viewerTransition.value = animate;
                viewerScale.value = 1;
                viewerOffset.x = 0;
                viewerOffset.y = 0;
            }

            function toggleViewerZoom() {
                clearViewerCloseTimer();
                if (!currentImage.value || currentImage.value.type === "video") return;
                if (currentImage.value.type === "folder") {
                    enterFolder(currentImage.value, true);
                    return;
                }
                viewerTransition.value = true;
                if (viewerScale.value > 1.05) {
                    resetViewerTransform(true);
                } else {
                    viewerScale.value = 2.5;
                    viewerOffset.x = 0;
                    viewerOffset.y = 0;
                }
            }

            function clearViewerCloseTimer() {
                if (!viewerCloseTimer) return;
                window.clearTimeout(viewerCloseTimer);
                viewerCloseTimer = 0;
            }

            function handleViewerClick() {
                if (Date.now() < suppressMouseClickUntil) return;
                clearViewerCloseTimer();
                viewerCloseTimer = window.setTimeout(() => {
                    viewerCloseTimer = 0;
                    closeViewer();
                }, 340);
            }

            function touchDistance(first, second) {
                return Math.hypot(second.clientX - first.clientX, second.clientY - first.clientY);
            }

            function handleViewerTouchStart(event) {
                if (viewerSlideAnimating.value) return;
                if (event.target && typeof event.target.closest === "function"
                    && event.target.closest(".cinema-viewer-video")) return;
                if (event.cancelable) event.preventDefault();
                clearViewerCloseTimer();
                clearImageLongPressTimer();
                viewerTransition.value = false;
                viewerSwipeTransition.value = false;
                if (event.touches.length >= 2) {
                    viewerSwipeOffset.value = 0;
                    touchState = {
                        mode: "pinch",
                        startDistance: Math.max(touchDistance(event.touches[0], event.touches[1]), 1),
                        startScale: viewerScale.value,
                        moved: true
                    };
                    return;
                }
                const touch = event.touches[0];
                if (!touch) return;
                touchState = {
                    mode: viewerScale.value > 1.05 ? "pan" : "swipe",
                    startX: touch.clientX,
                    startY: touch.clientY,
                    lastX: touch.clientX,
                    lastY: touch.clientY,
                    startOffsetX: viewerOffset.x,
                    startOffsetY: viewerOffset.y,
                    startedAt: Date.now(),
                    moved: false
                };
                armViewerImageLongPress();
            }

            function armViewerImageLongPress() {
                const state = touchState;
                const image = currentImage.value;
                if (!state || !image || image.type === "folder") return;
                const element = viewerRoot.value
                    ? viewerRoot.value.querySelector(".cinema-viewer-slide:nth-child(2) .cinema-viewer-image")
                    : null;
                imageLongPressTimer = window.setTimeout(() => {
                    imageLongPressTimer = 0;
                    if (touchState !== state || state.moved) return;
                    state.longPressed = true;
                    state.moved = true;
                    viewerSwipeOffset.value = 0;
                    suppressMouseClickUntil = Date.now() + 900;
                    showImageInfo(image, element,
                        !(element && element.dataset.thumbnailFallback === "true"));
                }, IMAGE_LONG_PRESS_MS);
            }

            function startViewerMouseLongPress(event) {
                if (currentImage.value && currentImage.value.type === "folder") return;
                if (!event || event.pointerType !== "mouse" || !event.isPrimary || event.button !== 0) return;
                if (event.target && typeof event.target.closest === "function"
                    && event.target.closest(".cinema-viewer-nav, .cinema-viewer-video")) return;
                clearViewerMouseLongPress();
                const element = event.currentTarget
                    ? event.currentTarget.querySelector(".cinema-viewer-slide:nth-child(2) .cinema-viewer-image")
                    : null;
                viewerMouseLongPressState = {
                    pointerId: event.pointerId,
                    startX: event.clientX,
                    startY: event.clientY,
                    longPressed: false
                };
                const state = viewerMouseLongPressState;
                imageLongPressTimer = window.setTimeout(() => {
                    imageLongPressTimer = 0;
                    if (viewerMouseLongPressState !== state || !currentImage.value) return;
                    state.longPressed = true;
                    suppressMouseClickUntil = Date.now() + 900;
                    showImageInfo(currentImage.value, element,
                        !(element && element.dataset.thumbnailFallback === "true"));
                }, IMAGE_LONG_PRESS_MS);
            }

            function trackViewerMouseLongPress(event) {
                const state = viewerMouseLongPressState;
                if (!state || state.pointerId !== event.pointerId || state.longPressed) return;
                if (Math.hypot(event.clientX - state.startX, event.clientY - state.startY)
                    > IMAGE_LONG_PRESS_MOVE_PX) {
                    clearViewerMouseLongPress();
                }
            }

            function finishViewerMouseLongPress(event) {
                const state = viewerMouseLongPressState;
                if (!state || state.pointerId !== event.pointerId) return;
                const longPressed = state.longPressed;
                clearViewerMouseLongPress();
                if (longPressed) {
                    event.preventDefault();
                    event.stopPropagation();
                }
            }

            function clearViewerMouseLongPress() {
                clearImageLongPressTimer();
                viewerMouseLongPressState = null;
            }

            function handleViewerTouchMove(event) {
                if (!touchState) return;
                if (event.cancelable) event.preventDefault();
                if (event.touches.length >= 2) {
                    clearImageLongPressTimer();
                    if (touchState.mode !== "pinch") {
                        viewerSwipeOffset.value = 0;
                        touchState = {
                            mode: "pinch",
                            startDistance: Math.max(touchDistance(event.touches[0], event.touches[1]), 1),
                            startScale: viewerScale.value,
                            moved: true
                        };
                    }
                    const distance = touchDistance(event.touches[0], event.touches[1]);
                    viewerScale.value = Math.min(5, Math.max(1, touchState.startScale * distance / touchState.startDistance));
                    return;
                }
                const touch = event.touches[0];
                if (!touch || touchState.mode === "pinch") return;
                const deltaX = touch.clientX - touchState.startX;
                const deltaY = touch.clientY - touchState.startY;
                touchState.lastX = touch.clientX;
                touchState.lastY = touch.clientY;
                if (Math.hypot(deltaX, deltaY) > 6) {
                    touchState.moved = true;
                    clearImageLongPressTimer();
                }
                if (touchState.mode === "pan" || viewerScale.value > 1.05) {
                    viewerOffset.x = touchState.startOffsetX + deltaX;
                    viewerOffset.y = touchState.startOffsetY + deltaY;
                } else {
                    const width = viewportWidth();
                    viewerSwipeOffset.value = Math.min(width, Math.max(-width, deltaX));
                }
            }

            function handleViewerTouchEnd(event) {
                if (!touchState) return;
                if (event.cancelable) event.preventDefault();
                clearImageLongPressTimer();
                if (event.touches && event.touches.length) {
                    if (event.touches.length === 1 && touchState && touchState.mode === "pinch") {
                        const touch = event.touches[0];
                        touchState = {
                            mode: viewerScale.value > 1.05 ? "pan" : "swipe",
                            startX: touch.clientX,
                            startY: touch.clientY,
                            lastX: touch.clientX,
                            lastY: touch.clientY,
                            startOffsetX: viewerOffset.x,
                            startOffsetY: viewerOffset.y,
                            startedAt: Date.now(),
                            moved: true
                        };
                    }
                    return;
                }
                const completedTouch = touchState;
                touchState = null;
                viewerTransition.value = true;

                if (completedTouch.longPressed) {
                    suppressMouseClickUntil = Date.now() + 900;
                    viewerSwipeOffset.value = 0;
                    viewerSwipeTransition.value = true;
                    return;
                }

                if (completedTouch.mode === "pinch") {
                    if (viewerScale.value <= 1.05) resetViewerTransform(true);
                    else clampViewerOffset();
                    suppressMouseClickUntil = Date.now() + 500;
                    return;
                }

                const deltaX = completedTouch.lastX - completedTouch.startX;
                const deltaY = completedTouch.lastY - completedTouch.startY;
                const elapsed = Math.max(Date.now() - completedTouch.startedAt, 1);
                const horizontalSwipe = completedTouch.mode === "swipe"
                    && Math.abs(deltaX) > Math.abs(deltaY) * 1.15;
                const passedDistance = Math.abs(deltaX) >= Math.min(viewportWidth() * .16, 120);
                const passedVelocity = Math.abs(deltaX) / elapsed >= .45;
                if (horizontalSwipe && (passedDistance || passedVelocity)) {
                    suppressMouseClickUntil = Date.now() + 500;
                    startViewerSlide(deltaX < 0 ? 1 : -1);
                    return;
                }

                if (viewerScale.value > 1.05) {
                    clampViewerOffset();
                } else if (completedTouch.mode === "swipe" && completedTouch.moved) {
                    settleViewerTrack();
                }
                if (!completedTouch.moved && Date.now() - completedTouch.startedAt < 350) {
                    handleViewerTap();
                } else {
                    suppressMouseClickUntil = Date.now() + 500;
                }
            }

            function handleViewerTap() {
                const now = Date.now();
                suppressMouseClickUntil = now + 700;
                if (now - lastTouchTapAt < 320) {
                    lastTouchTapAt = 0;
                    clearViewerCloseTimer();
                    toggleViewerZoom();
                    return;
                }
                lastTouchTapAt = now;
                clearViewerCloseTimer();
                viewerCloseTimer = window.setTimeout(() => {
                    viewerCloseTimer = 0;
                    lastTouchTapAt = 0;
                    closeViewer();
                }, 340);
            }

            function clampViewerOffset() {
                const imageElement = viewerRoot.value
                    ? viewerRoot.value.querySelector(".cinema-viewer-slide:nth-child(2) .cinema-viewer-image")
                    : null;
                if (viewerScale.value <= 1.05 || !imageElement) {
                    resetViewerTransform(true);
                    return;
                }
                const maxX = Math.max(0, imageElement.clientWidth * (viewerScale.value - 1) / 2);
                const maxY = Math.max(0, imageElement.clientHeight * (viewerScale.value - 1) / 2);
                viewerOffset.x = Math.min(maxX, Math.max(-maxX, viewerOffset.x));
                viewerOffset.y = Math.min(maxY, Math.max(-maxY, viewerOffset.y));
            }

            function handleViewerImageLoad() {
                clampViewerOffset();
            }

            function handleViewerSlideLoad(event, slide, slideIndex) {
                if (!slide || !slide.originalRequested) return;
                const element = event && event.currentTarget;
                if (slide.image && element && element.naturalWidth > 0 && element.naturalHeight > 0) {
                    slide.image.aspectRatio = element.naturalWidth / element.naturalHeight;
                }
                slide.originalReady = true;
                if (slideIndex === 1) handleViewerImageLoad();
            }

            function handleViewerVideoMetadata(event, slide) {
                if (!slide) return;
                const element = event && event.currentTarget;
                if (slide.image && element && element.videoWidth > 0 && element.videoHeight > 0) {
                    slide.image.aspectRatio = element.videoWidth / element.videoHeight;
                }
                slide.originalReady = true;
                slide.loadError = false;
            }

            function handleViewerVideoError(slide) {
                if (!slide) return;
                slide.originalReady = false;
                slide.loadError = true;
            }

            function pauseViewerVideos() {
                if (!viewerRoot.value) return;
                viewerRoot.value.querySelectorAll(".cinema-viewer-video video").forEach(video => {
                    if (typeof video.pause === "function") video.pause();
                });
            }

            function handleViewerImageError(event, slide) {
                const failedImage = currentImage.value;
                const element = event && event.currentTarget;
                if (failedImage && element && failedImage.thumbnailUrl
                    && element.dataset.thumbnailFallback !== "true") {
                    // A mobile browser may reject a very large original image. Keep
                    // the viewer usable by falling back to the generated thumbnail.
                    element.dataset.thumbnailFallback = "true";
                    element.src = failedImage.thumbnailUrl;
                    return;
                }
                if (slide) slide.loadError = true;
            }

            function hideViewerPreview(event) {
                if (event && event.currentTarget) event.currentTarget.style.visibility = "hidden";
            }

            function handleViewerSlideError(event, slide, slideIndex) {
                if (slideIndex === 1) handleViewerImageError(event, slide);
                else hideViewerPreview(event);
            }

            function preloadViewerImages(startIndex) {
                const urls = images.value.map(image => ["video", "folder"].includes(image.type)
                    ? null : image.url);
                if (!urls.length) return;
                const order = [];
                const visited = new Set();
                const add = (index) => {
                    const normalized = ((index % urls.length) + urls.length) % urls.length;
                    if (visited.has(normalized)) return;
                    visited.add(normalized);
                    if (urls[normalized]) order.push(urls[normalized]);
                };
                // The current image is loaded by the viewer itself. Preload only the
                // next 10 images; the old implementation walked the whole gallery.
                for (let distance = 1; distance <= MAX_FORWARD_PRELOAD; distance += 1) {
                    add(startIndex + distance);
                }
                preloadQueue = order;
                preloadWindowUrls = new Set(order);
                prunePreloadEntries(preloadWindowUrls);
                pumpPreloadQueue();
            }

            function handleGalleryImageError(event, image) {
                if (!image) return;
                const retryCount = image.thumbnailRetryCount || 0;
                if (event && event.currentTarget && retryCount < 2) {
                    image.thumbnailRetryCount = retryCount + 1;
                    image.loaded = false;
                    const baseUrl = (image.thumbnailUrl || image.url).split("&retry=")[0];
                    image.thumbnailUrl = `${baseUrl}&retry=${image.thumbnailRetryCount}`;
                    return;
                }
                const element = event && event.currentTarget;
                if (element && element.dataset.originalFallback !== "true" && image.url) {
                    element.dataset.originalFallback = "true";
                    element.src = image.url;
                    return;
                }
                image.loadError = true;
                image.loaded = true;
                scheduleGalleryLayout();
            }

            function markVideoLoaded(event, video) {
                if (!video) return;
                const element = event && event.currentTarget;
                if (element && element.naturalWidth > 0 && element.naturalHeight > 0) {
                    video.aspectRatio = element.naturalWidth / element.naturalHeight;
                }
                video.loadError = false;
                video.loaded = true;
                scheduleGalleryLayout();
            }

            function handleGalleryVideoError(video) {
                if (!video) return;
                video.loadError = true;
                video.loaded = true;
                scheduleGalleryLayout();
            }

            function pumpPreloadQueue() {
                while (preloadActiveCount < PRELOAD_CONCURRENCY && preloadQueue.length) {
                    const url = preloadQueue.shift();
                    if (preloadPromises.has(url)) continue;
                    preloadActiveCount += 1;
                    preloadOriginalImage(url).finally(() => {
                        preloadActiveCount -= 1;
                        prunePreloadEntries(preloadWindowUrls);
                        pumpPreloadQueue();
                    });
                }
            }

            function preloadOriginalImage(url) {
                const existing = preloadPromises.get(url);
                if (existing) return existing.promise;
                const entry = {image: null, pending: true, promise: null};
                entry.promise = new Promise((resolve) => {
                    const image = new Image();
                    entry.image = image;
                    const finish = () => {
                        if (!entry.pending) return;
                        const decoded = typeof image.decode === "function"
                            ? image.decode().catch(() => undefined)
                            : Promise.resolve();
                        decoded.then(() => {
                            if (!entry.pending) return;
                            entry.pending = false;
                            image.onload = null;
                            image.onerror = null;
                            entry.image = null;
                            resolve();
                        });
                    };
                    image.onload = finish;
                    image.onerror = () => {
                        if (!entry.pending) return;
                        entry.pending = false;
                        image.onload = null;
                        image.onerror = null;
                        entry.image = null;
                        resolve();
                    };
                    image.src = url;
                });
                preloadPromises.set(url, entry);
                return entry.promise;
            }

            function prunePreloadEntries(keepUrls) {
                preloadPromises.forEach((entry, url) => {
                    if (!entry.pending && !keepUrls.has(url)) preloadPromises.delete(url);
                });
            }

            function handleViewerKeydown(event) {
                if (activeRestorePosition) stopGalleryPositionRestore(true);
                if (!viewerVisible.value) return;
                if (document.fullscreenElement || (event.target && event.target.closest && event.target.closest(".cinema-viewer-video"))) return;
                if (event.key === "Escape") closeViewer();
                else if (event.key === "ArrowLeft") previousViewerImage();
                else if (event.key === "ArrowRight") nextViewerImage();
                else return;
                event.preventDefault();
            }

            function markImageLoaded(event, image) {
                if (!image) {
                    image = event;
                    event = null;
                }
                if (image) {
                    const element = event && event.currentTarget;
                    if (element && element.naturalWidth > 0 && element.naturalHeight > 0) {
                        image.aspectRatio = element.naturalWidth / element.naturalHeight;
                    }
                    image.loaded = true;
                    image.loadError = false;
                    scheduleGalleryLayout();
                }
            }

            function hideArtistImage(field) {
                if (field === "icon" || field === "banner") artist[field] = "";
            }

            function hideBrokenImage(image) {
                const index = images.value.indexOf(image);
                if (index < 0) return;
                images.value.splice(index, 1);
                renderedImageCount.value = Math.min(
                    Math.max(renderedImageCount.value, GALLERY_BATCH_SIZE), images.value.length);
                if (viewerIndex.value >= images.value.length) {
                    viewerIndex.value = Math.max(images.value.length - 1, 0);
                }
                scheduleGalleryLayout();
                scheduleGalleryBatchCheck();
            }

            function galleryPositionStorageKey() {
                return GALLERY_POSITION_STORAGE_PREFIX + String(folderPath.value || "unknown");
            }

            function readGalleryPosition() {
                try {
                    const raw = window.localStorage.getItem(galleryPositionStorageKey());
                    const value = raw ? JSON.parse(raw) : null;
                    if (!value || typeof value !== "object" || !Number.isFinite(value.scrollY)
                        || !Number.isFinite(value.offsetTop) || !Number.isInteger(value.index)) {
                        return null;
                    }
                    return value;
                } catch (error) {
                    return null;
                }
            }

            async function restoreGalleryBackPosition(position) {
                const top = Number(position && position.top) || 0;
                if (savedGalleryPosition && top >= 120
                    && Math.abs(savedGalleryPosition.scrollY - top) <= Math.max(250, top * .2)) {
                    resumePosition.value = savedGalleryPosition;
                    await restoreSavedGalleryPosition();
                    return;
                }
                dismissResumePrompt();
                if (top > 0 && images.value.length) {
                    renderedImageCount.value = Math.max(renderedImageCount.value,
                        Math.min(images.value.length, Math.ceil(top / 200) + GALLERY_BATCH_SIZE));
                    await nextTick();
                    layoutGalleryItems();
                    const requiredHeight = top + (window.innerHeight || 0);
                    while (renderedImageCount.value < images.value.length
                    && document.documentElement.scrollHeight < requiredHeight) {
                        renderedImageCount.value = Math.min(images.value.length,
                            renderedImageCount.value + GALLERY_BATCH_SIZE);
                        await nextTick();
                        layoutGalleryItems();
                    }
                }
                window.scrollTo(Number(position && position.left) || 0, top);
            }

            function offerSavedGalleryPosition() {
                if (resumeOfferConsumed || !savedGalleryPosition || !images.value.length) return;
                let index = images.value.findIndex(
                    image => galleryItemKey(image) === savedGalleryPosition.imageUrl);
                const exactImageMatch = index >= 0;
                if (index < 0 && savedGalleryPosition.index >= 0
                    && savedGalleryPosition.index < images.value.length) {
                    index = savedGalleryPosition.index;
                }
                if (index < 0 || (savedGalleryPosition.scrollY < 160 && index === 0)) {
                    resumeOfferConsumed = true;
                    return;
                }
                if (exactImageMatch && Number.isFinite(savedGalleryPosition.aspectRatio)
                    && savedGalleryPosition.aspectRatio > 0) {
                    images.value[index].aspectRatio = savedGalleryPosition.aspectRatio;
                }
                resumePosition.value = {...savedGalleryPosition, index};
                resumePromptVisible.value = true;
                if (resumePromptTimer) window.clearTimeout(resumePromptTimer);
                resumePromptTimer = window.setTimeout(dismissResumePrompt, GALLERY_RESUME_PROMPT_MS);
            }

            function dismissResumePrompt() {
                resumeOfferConsumed = true;
                resumePromptVisible.value = false;
                if (resumePromptTimer) {
                    window.clearTimeout(resumePromptTimer);
                    resumePromptTimer = 0;
                }
            }

            function handleGalleryScroll() {
                scheduleGalleryBatchCheck();
                scheduleGalleryPositionSave();
            }

            function scheduleGalleryPositionSave() {
                if (loading.value || viewerVisible.value || activeRestorePosition) return;
                if (positionSaveTimer) window.clearTimeout(positionSaveTimer);
                positionSaveTimer = window.setTimeout(() => {
                    positionSaveTimer = 0;
                    saveGalleryPosition();
                }, 180);
            }

            function saveGalleryPosition() {
                if (loading.value || activeRestorePosition) return;
                const scrollY = window.scrollY || document.documentElement.scrollTop || 0;
                if (scrollY < 120) {
                    try {
                        window.localStorage.removeItem(galleryPositionStorageKey());
                    } catch (error) {
                        // Storage may be disabled by the browser.
                    }
                    savedGalleryPosition = null;
                    return;
                }
                const grid = galleryGrid.value;
                if (!grid) return;
                const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                const referenceLine = Math.min(140, Math.max(72, viewportHeight * .18));
                const candidates = Array.from(grid.querySelectorAll(".gallery-item"))
                    .map(element => ({element, rect: element.getBoundingClientRect()}))
                    .filter(candidate => candidate.rect.bottom > 0 && candidate.rect.top < viewportHeight);
                if (!candidates.length) return;
                candidates.sort((left, right) => {
                    const leftDistance = left.rect.top <= referenceLine && left.rect.bottom >= referenceLine
                        ? 0 : Math.abs(left.rect.top - referenceLine);
                    const rightDistance = right.rect.top <= referenceLine && right.rect.bottom >= referenceLine
                        ? 0 : Math.abs(right.rect.top - referenceLine);
                    return leftDistance - rightDistance || left.rect.left - right.rect.left;
                });
                const anchor = candidates[0];
                const index = Number.parseInt(anchor.element.dataset.galleryIndex, 10);
                const image = images.value[index];
                if (!image || !Number.isInteger(index)) return;
                const position = {
                    imageUrl: galleryItemKey(image),
                    imageName: image.name,
                    index,
                    offsetTop: anchor.rect.top,
                    aspectRatio: image.loaded && anchor.rect.width > 0 && anchor.rect.height > 0
                        ? anchor.rect.width / anchor.rect.height : null,
                    scrollY,
                    sortField: sort.field,
                    sortOrder: sort.order,
                    savedAt: Date.now()
                };
                try {
                    window.localStorage.setItem(galleryPositionStorageKey(), JSON.stringify(position));
                    savedGalleryPosition = position;
                } catch (error) {
                    // A full or disabled localStorage must not affect gallery browsing.
                }
            }

            async function restoreSavedGalleryPosition() {
                const position = resumePosition.value;
                if (!position || !images.value.length) return;
                dismissResumePrompt();
                stopGalleryPositionRestore(false);
                let targetIndex = images.value.findIndex(
                    image => galleryItemKey(image) === position.imageUrl);
                if (targetIndex < 0) {
                    targetIndex = Math.min(Math.max(position.index, 0), images.value.length - 1);
                }
                activeRestorePosition = {...position, index: targetIndex};
                renderedImageCount.value = Math.max(renderedImageCount.value,
                    Math.min(images.value.length, targetIndex + GALLERY_BATCH_SIZE));
                await nextTick();
                if (!activeRestorePosition) return;
                layoutGalleryItems();
                restoreDeadlineTimer = window.setTimeout(
                    () => stopGalleryPositionRestore(true), GALLERY_RESTORE_STABILIZE_MS);
            }

            function alignRestoredGalleryPosition() {
                if (!activeRestorePosition || !galleryGrid.value) return;
                let targetIndex = images.value.findIndex(
                    image => galleryItemKey(image) === activeRestorePosition.imageUrl);
                if (targetIndex < 0) {
                    targetIndex = Math.min(activeRestorePosition.index, images.value.length - 1);
                }
                const element = Array.from(galleryGrid.value.querySelectorAll(".gallery-item"))
                    .find(item => Number.parseInt(item.dataset.galleryIndex, 10) === targetIndex);
                if (!element) return;
                const delta = element.getBoundingClientRect().top - activeRestorePosition.offsetTop;
                if (Math.abs(delta) > .5) {
                    const currentY = window.scrollY || document.documentElement.scrollTop || 0;
                    window.scrollTo(window.scrollX || 0, Math.max(0, currentY + delta));
                }
                const stable = images.value.slice(0, targetIndex + 1).every(image => image.loaded);
                if (stable && !restoreStableTimer) {
                    restoreStableTimer = window.setTimeout(
                        () => stopGalleryPositionRestore(true), 350);
                } else if (!stable && restoreStableTimer) {
                    window.clearTimeout(restoreStableTimer);
                    restoreStableTimer = 0;
                }
            }

            function stopGalleryPositionRestore(saveAfter = true) {
                activeRestorePosition = null;
                if (restoreDeadlineTimer) window.clearTimeout(restoreDeadlineTimer);
                if (restoreStableTimer) window.clearTimeout(restoreStableTimer);
                restoreDeadlineTimer = 0;
                restoreStableTimer = 0;
                if (saveAfter) scheduleGalleryPositionSave();
            }

            function cancelGalleryPositionRestore() {
                if (activeRestorePosition) stopGalleryPositionRestore(true);
            }

            function handleSortChange() {
                try{window.localStorage.setItem(sortStorageKey(),JSON.stringify({field:sort.field,order:sort.order}));}catch{}
                dismissResumePrompt();
                stopGalleryPositionRestore(false);
                savedGalleryPosition = null;
                try {
                    window.localStorage.removeItem(galleryPositionStorageKey());
                } catch (error) {
                    // Storage may be disabled by the browser.
                }
                loadGallery(false);
            }

            function scheduleGalleryBatchCheck() {
                if (galleryBatchFrame) return;
                galleryBatchFrame = window.requestAnimationFrame(() => {
                    galleryBatchFrame = 0;
                    maybeLoadMoreGalleryImages();
                });
            }

            function maybeLoadMoreGalleryImages() {
                if (renderedImageCount.value >= images.value.length) return;
                const grid = galleryGrid.value;
                if (!grid) return;
                const viewportBottom = (window.innerHeight || document.documentElement.clientHeight || 0)
                    + GALLERY_LOAD_AHEAD_PX;
                if (grid.getBoundingClientRect().bottom > viewportBottom) return;
                renderedImageCount.value = Math.min(
                    renderedImageCount.value + GALLERY_BATCH_SIZE, images.value.length);
                scheduleGalleryLayout();
                nextTick(scheduleGalleryBatchCheck);
            }

            function goBack() {
                router.back();
            }

            function scheduleGalleryLayout() {
                nextTick(() => {
                    if (layoutFrame) window.cancelAnimationFrame(layoutFrame);
                    layoutFrame = window.requestAnimationFrame(layoutGalleryItems);
                });
            }

            function layoutGalleryItems() {
                layoutFrame = 0;
                const grid = galleryGrid.value;
                if (!grid) return;
                const styles = window.getComputedStyle(grid);
                const rowHeight = Number.parseFloat(styles.gridAutoRows) || 8;
                const rowGap = Number.parseFloat(styles.rowGap) || 16;
                grid.querySelectorAll(".gallery-item").forEach((item) => {
                    const image = item.querySelector("img, video");
                    const width = item.getBoundingClientRect().width;
                    const index = Number.parseInt(item.dataset.galleryIndex, 10);
                    const galleryImage = images.value[index];
                    if (galleryImage && galleryImage.type === "folder") {
                        const poster = item.querySelector(".gallery-folder-poster-image");
                        const ratio = poster && poster.naturalWidth > 0 && poster.naturalHeight > 0
                            ? poster.naturalWidth / poster.naturalHeight : galleryImage.aspectRatio;
                        const folderHeight = galleryImage.posterUrl && Number.isFinite(ratio) && ratio > 0
                            ? Math.max(180, Math.ceil(Math.max(1, width - 16) / ratio) + 66)
                            : Math.max(210, Math.round(width * .9));
                        item.style.height = `${folderHeight}px`;
                        item.style.gridRowEnd = `span ${Math.max(1,
                            Math.ceil((folderHeight + rowGap) / (rowHeight + rowGap)))}`;
                        return;
                    }
                    const mediaWidth = image && image.naturalWidth;
                    const mediaHeight = image && image.naturalHeight;
                    const imageLoaded = image && !image.classList.contains("is-loading")
                        && mediaWidth > 0 && mediaHeight > 0;
                    const height = imageLoaded
                        ? Math.ceil(width * mediaHeight / mediaWidth)
                        : galleryImage && Number.isFinite(galleryImage.aspectRatio)
                            && galleryImage.aspectRatio > 0
                            ? Math.ceil(width / galleryImage.aspectRatio) : 210;
                    item.style.height = `${height}px`;
                    const span = Math.max(1, Math.ceil((height + rowGap) / (rowHeight + rowGap)));
                    item.style.gridRowEnd = `span ${span}`;
                });
                // Keep the saved item at the same viewport offset in this layout frame,
                // before the browser paints height changes from newly loaded images.
                if (activeRestorePosition) alignRestoredGalleryPosition();
            }

            watch(() => queryValue(route.query.folderPath), async (nextFolderPath) => {
                if (!nextFolderPath || nextFolderPath === folderPath.value) return;
                if (!viewerVisible.value) saveGalleryPosition();
                if(downloadRequest)downloadRequest.abort();
                folderPath.value = nextFolderPath;
                savedGalleryPosition = readGalleryPosition();
                const preference=readSavedSort();
                sort.field=preference?.field || savedGalleryPosition?.sortField || "album";
                sort.order=preference?.order || savedGalleryPosition?.sortOrder || "desc";
                resumeOfferConsumed = false;
                dismissResumePrompt();
                stopGalleryPositionRestore(false);
                const keepViewerOpen = pendingViewerFolderNavigation;
                pendingViewerFolderNavigation = false;
                const loaded = await loadGallery(!keepViewerOpen);
                if (!keepViewerOpen) return;
                if (!loaded || !images.value.length) {
                    closeViewer();
                    return;
                }
                viewerIndex.value = 0;
                viewerOpeningVisibleUrls = new Set();
                rebuildViewerSlides();
                resetViewerTransform(false);
                resetViewerTrack(false);
                nextTick(() => {
                    viewerSwipeTransition.value = true;
                    if (viewerRoot.value) viewerRoot.value.focus({preventScroll: true});
                });
                preloadViewerImages(0);
            });

            onMounted(() => {
                loadGallery();
                loadArtist();
                window.addEventListener("resize", scheduleGalleryLayout);
                window.addEventListener("resize", scheduleGalleryBatchCheck);
                window.addEventListener("scroll", handleGalleryScroll, {passive: true});
                window.addEventListener("pointerdown", cancelGalleryPositionRestore, {passive: true});
                window.addEventListener("touchstart", cancelGalleryPositionRestore, {passive: true});
                window.addEventListener("wheel", cancelGalleryPositionRestore, {passive: true});
                window.addEventListener("keydown", handleViewerKeydown);
                window.addEventListener("popstate", handleViewerPopstate);
            });

            onBeforeUnmount(() => {
                uploadDisposed=true;if(uploadRequest)uploadRequest.abort();if(downloadRequest)downloadRequest.abort();
                galleryLoadId += 1;
                window.removeEventListener("resize", scheduleGalleryLayout);
                window.removeEventListener("resize", scheduleGalleryBatchCheck);
                window.removeEventListener("scroll", handleGalleryScroll);
                window.removeEventListener("pointerdown", cancelGalleryPositionRestore);
                window.removeEventListener("touchstart", cancelGalleryPositionRestore);
                window.removeEventListener("wheel", cancelGalleryPositionRestore);
                window.removeEventListener("keydown", handleViewerKeydown);
                window.removeEventListener("popstate", handleViewerPopstate);
                if (!viewerVisible.value && !activeRestorePosition && !pendingViewerGalleryReturn) {
                    saveGalleryPosition();
                }
                if (layoutFrame) window.cancelAnimationFrame(layoutFrame);
                if (galleryBatchFrame) window.cancelAnimationFrame(galleryBatchFrame);
                if (positionSaveTimer) window.clearTimeout(positionSaveTimer);
                dismissResumePrompt();
                stopGalleryPositionRestore(false);
                pendingViewerGalleryReturn = null;
                viewerOpeningVisibleUrls = new Set();
                cancelGalleryImageLongPress();
                clearViewerMouseLongPress();
                closeImageInfo();
                clearViewerCloseTimer();
                clearViewerSlideTimer();
                preloadQueue = [];
                preloadWindowUrls = new Set();
                prunePreloadEntries(new Set());
                unlockViewerScroll();
            });

            return {
                uploadInput,uploading,uploadProgress,canUpload,uploadFile,downloading,canDownload,downloadAll,
                artist,
                artistName,
                folderPath,
                hasArtist,
                cancelGalleryImageLongPress,
                clearViewerMouseLongPress,
                closeImageInfo,
                closeViewer,
                currentImage,
                errorMessage,
                finishGalleryImageLongPress,
                finishViewerMouseLongPress,
                galleryGrid,
                handleGalleryItemClick,
                handleGalleryImageError,
                handleGalleryVideoError,
                handleViewerClick,
                handleViewerAfterLeave,
                handleViewerImageError,
                handleViewerImageLoad,
                handleViewerPopstate,
                handleViewerSlideError,
                handleViewerSlideLoad,
                handleViewerVideoError,
                handleViewerVideoMetadata,
                handleViewerTrackTransitionEnd,
                handleViewerTouchEnd,
                handleViewerTouchMove,
                handleViewerTouchStart,
                hideArtistImage,
                hideBrokenImage,
                hideViewerPreview,
                imageInfo,
                imageInfoVisible,
                images,
                dismissResumePrompt,
                handleSortChange,
                renderedImageCount,
                resumePosition,
                resumePromptVisible,
                restoreSavedGalleryPosition,
                visibleImages,
                loadGallery,
                loading,
                markImageLoaded,
                markVideoLoaded,
                openViewer,
                goBack,
                nextViewerImage,
                previousViewerImage,
                sort,
                startGalleryImageLongPress,
                startViewerMouseLongPress,
                trackGalleryImageLongPress,
                trackViewerMouseLongPress,
                toggleViewerZoom,
                viewerImageStyle,
                viewerIndex,
                viewerRoot,
                viewerSlides,
                viewerTrackStyle,
                viewerVisible
            };
        }
    };

    function queryValue(value) {
        if (Array.isArray(value)) return value.length ? String(value[0] || "") : "";
        return value === null || value === undefined ? "" : String(value);
    }

    async function requestData(url) {
        const response = await fetch(url, {headers: {Accept: "application/json"}, cache: "no-store"});
        if (response.status === 401) {
            const target = location.pathname + location.search + location.hash;
            location.replace(`/login.html?redirect=${encodeURIComponent(target)}`);
        }
        let result;
        try {
            result = await response.json();
        } catch (error) {
            throw new Error(`HTTP ${response.status}`);
        }
        if (!response.ok || result.result !== "SUCCESS") {
            throw new Error(result.message || `HTTP ${response.status}`);
        }
        return result.data;
    }
})();
