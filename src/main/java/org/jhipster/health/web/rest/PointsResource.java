package org.jhipster.health.web.rest;

import com.codahale.metrics.annotation.Timed;
import org.jhipster.health.domain.Points;
import org.jhipster.health.repository.PointsRepository;
import org.jhipster.health.repository.PointsThisWeek;
import org.jhipster.health.repository.UserRepository;
import org.jhipster.health.repository.search.PointsSearchRepository;
import org.jhipster.health.security.AuthoritiesConstants;
import org.jhipster.health.security.SecurityUtils;
import org.jhipster.health.web.rest.util.HeaderUtil;
import org.jhipster.health.web.rest.util.PaginationUtil;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.queryString;

/**
 * REST controller for managing Points.
 */
@RestController
@RequestMapping("/api")
public class PointsResource {

    private final Logger log = LoggerFactory.getLogger(PointsResource.class);

    @Inject
    private PointsRepository pointsRepository;

    @Inject
    private PointsSearchRepository pointsSearchRepository;

    @Inject
    private UserRepository userRepository;

    /**
     * POST  /points -> Create a new points.
     */
    @RequestMapping(value = "/points",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Points> create(@Valid @RequestBody Points points) throws URISyntaxException {
        log.debug("REST request to save Points : {}", points);
        if (points.getId() != null) {
            return ResponseEntity.badRequest().header("Failure", "A new points cannot already have an ID").body(null);
        }
        if (points.getUser() == null || points.getUser().getId() == null) {
            log.debug("No user passed in, using current user: {}", SecurityUtils.getCurrentLogin());
            points.setUser(userRepository.findOneByLogin(SecurityUtils.getCurrentLogin()).get());
        }
        Points result = pointsRepository.save(points);
        pointsSearchRepository.save(result);
        return ResponseEntity.created(new URI("/api/points/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("points", result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /points -> Updates an existing points.
     */
    @RequestMapping(value = "/points",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Points> update(@Valid @RequestBody Points points) throws URISyntaxException {
        log.debug("REST request to update Points : {}", points);
        if (points.getId() == null) {
            return create(points);
        }
        Points result = pointsRepository.save(points);
        pointsSearchRepository.save(points);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("points", points.getId().toString()))
            .body(result);
    }

    /**
     * GET  /points -> get all the points.
     */
    @RequestMapping(value = "/points",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Points>> getAll(@RequestParam(value = "page", required = false) Integer offset,
                                               @RequestParam(value = "per_page", required = false) Integer limit)
        throws URISyntaxException {
        Page<Points> page;
        if (SecurityUtils.isUserInRole(AuthoritiesConstants.ADMIN)) {
            page = pointsRepository.findAllByOrderByDateDesc(PaginationUtil.generatePageRequest(offset, limit));
        } else {
            page = pointsRepository.findAllForCurrentUser(PaginationUtil.generatePageRequest(offset, limit));
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/points", offset, limit);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET  /points -> get all the points.
     */
    @RequestMapping(value = "/points-this-week",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<PointsThisWeek> getPointsThisWeek() throws URISyntaxException {
        List<Points> points = pointsRepository.findAllThisWeek();

        Integer numPoints = 0;
        // todo: use Java 8 streams/filters
        String currentLogin = SecurityUtils.getCurrentLogin();
        for (Points p : points) {
            // only count if points belongs to the current user
            if (p.getUser() != null && p.getUser().getLogin().equals(currentLogin)) {
                numPoints += p.getExercise() + p.getMeals() + p.getAlcohol();
            }
        }

        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.withDayOfWeek(DateTimeConstants.MONDAY);
        PointsThisWeek count = new PointsThisWeek(weekStart, numPoints);

        return new ResponseEntity<>(count, HttpStatus.OK);
    }

    /**
     * GET  /points/:id -> get the "id" points.
     */
    @RequestMapping(value = "/points/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Points> get(@PathVariable Long id) {
        log.debug("REST request to get Points : {}", id);
        return Optional.ofNullable(pointsRepository.findOne(id))
            .map(points -> new ResponseEntity<>(
                points,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /points/:id -> delete the "id" points.
     */
    @RequestMapping(value = "/points/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.debug("REST request to delete Points : {}", id);
        pointsRepository.delete(id);
        pointsSearchRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("points", id.toString())).build();
    }

    /**
     * SEARCH  /_search/points/:query -> search for the points corresponding
     * to the query.
     */
    @RequestMapping(value = "/_search/points/{query}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Points> search(@PathVariable String query) {
        return StreamSupport
            .stream(pointsSearchRepository.search(queryString(query)).spliterator(), false)
            .collect(Collectors.toList());
    }
}
