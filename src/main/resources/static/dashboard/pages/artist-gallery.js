(() => {
    "use strict";

    const {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref} = Vue;
    const {useRoute, useRouter} = VueRouter;
    window.DazoAdminPages = window.DazoAdminPages || {};

    window.DazoAdminPages.ArtistGallery = {
        template: "#artist-gallery-template",
        setup() {
            const route = useRoute();
            const router = useRouter();
            const artist = reactive({
                name: route.query.name || "画师图库",
                icon: "",
                banner: "",
                url: "",
                artistDirectory: ""
            });
            const galleryGrid = ref(null);
            const images = ref([]);
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
            const sort = reactive({field: "album", order: "desc"});
            const artistName = computed(() => artist.name || route.query.name || "画师图库");
            const currentImage = computed(() => images.value[viewerIndex.value] || null);
            const viewerImageStyle = computed(() => ({
                transform: `translate3d(${viewerOffset.x}px, ${viewerOffset.y}px, 0) scale(${viewerScale.value})`,
                transition: viewerTransition.value ? "transform .22s ease-out" : "none"
            }));
            const viewerTrackStyle = computed(() => ({
                transform: `translate3d(calc(-100vw + ${viewerSwipeOffset.value}px), 0, 0)`,
                transition: viewerSwipeTransition.value
                    ? `transform ${viewerSwipeDuration.value}ms cubic-bezier(.22, .61, .36, 1)` : "none"
            }));

            let layoutFrame = 0;
            let viewerCloseTimer = 0;
            let lastTouchTapAt = 0;
            let suppressMouseClickUntil = 0;
            let touchState = null;
            let previousBodyOverflow = "";
            let preloadGeneration = 0;
            let viewerSlideTimer = 0;
            let pendingViewerDirection = 0;
            let viewerSlideId = 0;
            let viewerCompletingSlide = false;
            const preloadPromises = new Map();

            async function loadGallery() {
                loading.value = true;
                errorMessage.value = "";
                try {
                    const params = new URLSearchParams({sortBy: sort.field, order: sort.order});
                    const data = await requestData(`/api/fanbox/${encodeURIComponent(route.params.id)}/gallery?${params}`);
                    if (data && data.artist) {
                        artist.name = data.artist.name || artist.name;
                        artist.icon = data.artist.icon || "";
                        artist.banner = data.artist.banner || "";
                        artist.url = data.artist.url || "";
                        artist.artistDirectory = data.artist.artistDirectory || "";
                    }
                    images.value = data && Array.isArray(data.images)
                        ? data.images.map(image => ({...image, loaded: false}))
                        : [];
                    viewerIndex.value = Math.min(viewerIndex.value, Math.max(images.value.length - 1, 0));
                    scheduleGalleryLayout();
                } catch (error) {
                    errorMessage.value = `图库加载失败：${error.message}`;
                } finally {
                    loading.value = false;
                }
            }

            function openViewer(index) {
                if (!images.value.length) return;
                clearViewerCloseTimer();
                viewerIndex.value = normalizeViewerIndex(index);
                rebuildViewerSlides();
                resetViewerTransform(false);
                resetViewerTrack(false);
                viewerVisible.value = true;
                previousBodyOverflow = document.body.style.overflow;
                document.body.style.overflow = "hidden";
                nextTick(() => {
                    if (viewerRoot.value) viewerRoot.value.focus({preventScroll: true});
                    window.requestAnimationFrame(() => {
                        viewerSwipeTransition.value = true;
                    });
                });
                preloadViewerImages(viewerIndex.value);
            }

            function closeViewer() {
                if (!viewerVisible.value) return;
                clearViewerCloseTimer();
                clearViewerSlideTimer();
                viewerVisible.value = false;
                touchState = null;
            }

            function handleViewerAfterLeave() {
                viewerSlides.value = [];
                document.body.style.overflow = previousBodyOverflow;
                resetViewerTransform(false);
                resetViewerTrack(false);
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

            function createViewerSlide(image) {
                viewerSlideId += 1;
                return {id: viewerSlideId, image};
            }

            function rebuildViewerSlides() {
                if (!images.value.length) {
                    viewerSlides.value = [];
                    return;
                }
                viewerSlides.value = [
                    createViewerSlide(imageAt(viewerIndex.value - 1)),
                    createViewerSlide(imageAt(viewerIndex.value)),
                    createViewerSlide(imageAt(viewerIndex.value + 1))
                ];
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
                resetViewerTransform(true);
                pendingViewerDirection = direction < 0 ? -1 : 1;
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
                clearViewerCloseTimer();
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
            }

            function handleViewerTouchMove(event) {
                if (!touchState) return;
                if (event.touches.length >= 2) {
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
                if (Math.hypot(deltaX, deltaY) > 6) touchState.moved = true;
                if (touchState.mode === "pan" || viewerScale.value > 1.05) {
                    viewerOffset.x = touchState.startOffsetX + deltaX;
                    viewerOffset.y = touchState.startOffsetY + deltaY;
                } else {
                    const width = viewportWidth();
                    viewerSwipeOffset.value = Math.min(width, Math.max(-width, deltaX));
                }
            }

            function handleViewerTouchEnd(event) {
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
                if (!touchState) return;
                const completedTouch = touchState;
                touchState = null;
                viewerTransition.value = true;

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

            function handleViewerImageError() {
                const failedImage = currentImage.value;
                if (failedImage) hideBrokenImage(failedImage);
                if (!images.value.length) {
                    closeViewer();
                    return;
                }
                viewerIndex.value = normalizeViewerIndex(viewerIndex.value);
                rebuildViewerSlides();
                resetViewerTransform(false);
                resetViewerTrack(false);
                nextTick(() => {
                    viewerSwipeTransition.value = true;
                });
            }

            function hideViewerPreview(event) {
                if (event && event.currentTarget) event.currentTarget.style.visibility = "hidden";
            }

            function handleViewerSlideError(event, slideIndex) {
                if (slideIndex === 1) handleViewerImageError();
                else hideViewerPreview(event);
            }

            function preloadViewerImages(startIndex) {
                const urls = images.value.map(image => image.url).filter(Boolean);
                if (!urls.length) return;
                const order = [];
                const visited = new Set();
                const add = (index) => {
                    const normalized = ((index % urls.length) + urls.length) % urls.length;
                    if (visited.has(normalized)) return;
                    visited.add(normalized);
                    order.push(urls[normalized]);
                };
                add(startIndex);
                for (let distance = 1; order.length < urls.length; distance += 1) {
                    add(startIndex + distance);
                    add(startIndex - distance);
                }
                const generation = ++preloadGeneration;
                preloadSequentially(order, generation);
            }

            async function preloadSequentially(urls, generation) {
                for (const url of urls) {
                    if (generation !== preloadGeneration) return;
                    await preloadOriginalImage(url);
                }
            }

            function preloadOriginalImage(url) {
                if (preloadPromises.has(url)) return preloadPromises.get(url);
                const promise = new Promise((resolve) => {
                    const image = new Image();
                    image.onload = () => {
                        if (typeof image.decode === "function") image.decode().catch(() => {
                        }).then(resolve);
                        else resolve();
                    };
                    image.onerror = resolve;
                    image.src = url;
                });
                preloadPromises.set(url, promise);
                return promise;
            }

            function handleViewerKeydown(event) {
                if (!viewerVisible.value) return;
                if (event.key === "Escape") closeViewer();
                else if (event.key === "ArrowLeft") previousViewerImage();
                else if (event.key === "ArrowRight") nextViewerImage();
                else return;
                event.preventDefault();
            }

            function markImageLoaded(image) {
                if (image) {
                    image.loaded = true;
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
                if (viewerIndex.value >= images.value.length) {
                    viewerIndex.value = Math.max(images.value.length - 1, 0);
                }
                scheduleGalleryLayout();
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
                    const image = item.querySelector("img");
                    const width = item.getBoundingClientRect().width;
                    const imageLoaded = image && !image.classList.contains("is-loading")
                        && image.naturalWidth > 0 && image.naturalHeight > 0;
                    const height = imageLoaded
                        ? Math.ceil(width * image.naturalHeight / image.naturalWidth)
                        : 210;
                    item.style.height = `${height}px`;
                    const span = Math.max(1, Math.ceil((height + rowGap) / (rowHeight + rowGap)));
                    item.style.gridRowEnd = `span ${span}`;
                });
            }

            onMounted(() => {
                loadGallery();
                window.addEventListener("resize", scheduleGalleryLayout);
                window.addEventListener("keydown", handleViewerKeydown);
            });

            onBeforeUnmount(() => {
                window.removeEventListener("resize", scheduleGalleryLayout);
                window.removeEventListener("keydown", handleViewerKeydown);
                if (layoutFrame) window.cancelAnimationFrame(layoutFrame);
                clearViewerCloseTimer();
                clearViewerSlideTimer();
                preloadGeneration += 1;
                document.body.style.overflow = previousBodyOverflow;
            });

            return {
                artist,
                artistName,
                closeViewer,
                currentImage,
                errorMessage,
                galleryGrid,
                handleViewerClick,
                handleViewerAfterLeave,
                handleViewerImageError,
                handleViewerImageLoad,
                handleViewerSlideError,
                handleViewerTrackTransitionEnd,
                handleViewerTouchEnd,
                handleViewerTouchMove,
                handleViewerTouchStart,
                hideArtistImage,
                hideBrokenImage,
                images,
                loadGallery,
                loading,
                markImageLoaded,
                openViewer,
                goBack,
                nextViewerImage,
                previousViewerImage,
                sort,
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
